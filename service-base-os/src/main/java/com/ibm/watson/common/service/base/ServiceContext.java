/*
 * (C) Copyright IBM Corp. 2015, 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.ibm.watson.common.service.base;

import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Singleton object to store service properties for global access.
 *
 */
public class ServiceContext {
	private static final Logger logger = LoggerFactory.getLogger(ServiceContext.class.getName());

	public static final String HEADER_X_WATSON_DP_URL_IN = "X-Watson-DP-URL-in";
	public static final String ENABLED_DARK_FEATURES = "enabled.dark.features";

	public static final String AUTHENTICATION_TYPE_PROPERTY =   "com_ibm_watson_health_services_authentication_type";
	public static final String AUTHENTICATION_HEADER_PROPERTY = "com_ibm_watson_health_services_authentication_header";
	
	public static final String ENABLE_METRICS_PROPERTY = "com_ibm_watson_health_services_enable_metrics";

	private static volatile ServiceContext instance;

	private static AtomicLong requestCount = new AtomicLong(0L);

	private static ServiceConcurrentLimit concurrentLimit;

	private String contextRoot;
	private Properties serviceProperties;
	private String externalVersion = "0.0.0";
	private String majorVersion = "0";
	private String minorVersion = "0";
	private String fixVersion = "0";  // Could also be a build number
	private String buildVersionSuffix = "";
	private String buildTime = "0000/00/00.000000";

	private AuthenticationType authenticationType;
	private String authenticationHeader;

	public enum AuthenticationType {
		legacy,			// Old behavior that looks for different headers in order
		apim,			// Header X-IBM-Client-ID
		datapower,		// Header X-Watson-UserInfo, Sub-headers: OrgId, UserId
		datapower_test,	// Header X-Watson-UserInfo, Sub-headers: OrgId, UserId, with tenant override
		deadbolt,		// Header X-Watson-UserInfo, Sub-header: bluemix-instance-id
		custom_header	// Header set by property AUTHENTICATION_HEADER_PROPERTY
	};

	private static final String HOSTKEY = "host";
	private static final String SCHEMEKEY = "scheme";
	//indexed by url and then nested hashtable is indexed by component name
	private static Hashtable<String, Hashtable<String,String>> cachedDPUrl = new Hashtable<String, Hashtable<String,String>>();

	private static Set<String> enabledDarkFeatureSet;

	private volatile boolean initializationFailed = false;
	
	private static boolean enableMetrics = false;
	private static ServiceMetrics serviceMetrics;

	/**
	 * Get service context.  This uses the service.properties file on the classpath.
	 *
	 * @return
	 */
	public static synchronized ServiceContext createInstance() {
		if(instance == null) {
			instance = new ServiceContext();
		}

		return instance;
	}

	/**
	 * Get the service context instance.  It must be created before this call.
	 * @return Singleton instance
	 */
	public static ServiceContext getInstance() { // Should not need to lock
		if(instance == null) {
			throw new IllegalStateException("ServiceContext not initialized");
		}

		return instance;
	}

	protected ServiceContext() {
		// Load service properties
		String propertiesPath = "service.properties";
		InputStream inputStream = null;
		try {
			try{
				Properties p = new Properties();
				inputStream = getClass().getClassLoader().getResourceAsStream(propertiesPath);
				if (inputStream != null) {
					logger.info("service.properties used");
				    p.load(inputStream);
				} else {
					propertiesPath = "/com/ibm/watson/common/service/service.properties";
					inputStream = getClass().getClassLoader().getResourceAsStream(propertiesPath);
					if (inputStream != null) {
						logger.info("service.properties used from legacy location");
					    p.load(inputStream);
					} else {
						logger.info("service.properties not used");
					}
				}

				serviceProperties = p;

				buildTime = serviceProperties.getProperty(ServiceBaseConstants.BUILD_TIMESTAMP);

				// Add the individual version properties parsed from the build version
				String buildVersion = serviceProperties.getProperty(ServiceBaseConstants.BUILD_VERSION);
				if (buildVersion != null && !buildVersion.isEmpty()) {
					setBuildVersion(buildVersion);
				}
			} catch (Throwable e) {
				logger.error("Exception loading "+propertiesPath+" file: "+e);
				throw new IllegalArgumentException("Exception loading config file: "+propertiesPath, e);
			} finally {
				try {
					inputStream.close();
				} catch (Exception e) {	}
			}

			// Get list of enabled dark features from system property. Defaults to none
			// which means none are available.
			String enabledDarkFeaturesProperty = System.getProperty(ENABLED_DARK_FEATURES, "none");
			if (enabledDarkFeaturesProperty.equalsIgnoreCase("all")) {
				enabledDarkFeatureSet = null;
				logger.info("Property: " + ENABLED_DARK_FEATURES + "=all");
			} else {
				enabledDarkFeatureSet = new HashSet<String>();
				if (enabledDarkFeaturesProperty.equalsIgnoreCase("none")) {
					logger.info("Property: " + ENABLED_DARK_FEATURES + "=none");
				} else {
					String[] values = enabledDarkFeaturesProperty.split(",");
					enabledDarkFeatureSet.addAll(Arrays.asList(values));
					logger.info("Property: " + ENABLED_DARK_FEATURES + "=" + enabledDarkFeatureSet);
				}
			}

			// Create concurrent limit object
			// TODO - catch exception here?????
			concurrentLimit = ServiceConcurrentLimit.createInstance(serviceProperties);

			// Process tenant related properties

			// Get and validate authentication type property
			String authTypeValue = serviceProperties.getProperty(AUTHENTICATION_TYPE_PROPERTY, AuthenticationType.legacy.toString());
			try {
				authenticationType = AuthenticationType.valueOf(authTypeValue);
			}
			catch(IllegalArgumentException e) {
				// Invalid value for AUTHENTICATION_TYPE_PROPERTY
				logger.error("Error, invalid value for property " + AUTHENTICATION_TYPE_PROPERTY + ", value=" + authTypeValue);
				authenticationType = null;
				throw e;
			}
			logger.info("Authentication type property "+AUTHENTICATION_TYPE_PROPERTY+"="+authenticationType);

			// Get authentication tenant header property.  This property will specify which header to use for the
			// tenant value when the authentication_type property is set to 'custom_header'.
			authenticationHeader = serviceProperties.getProperty(AUTHENTICATION_HEADER_PROPERTY);
			if( authenticationType == AuthenticationType.custom_header && (authenticationHeader == null || authenticationHeader.isEmpty()) ) {
				logger.error("Error, property " + AUTHENTICATION_HEADER_PROPERTY + " is required when " +
						AUTHENTICATION_TYPE_PROPERTY + " is set to " + AuthenticationType.custom_header);
				throw new IllegalArgumentException("Property " + AUTHENTICATION_HEADER_PROPERTY + " is required when " +
						AUTHENTICATION_TYPE_PROPERTY + " is set to " + AuthenticationType.custom_header);
			}
			else {
				logger.info("Authentication header property "+AUTHENTICATION_HEADER_PROPERTY+"="+authenticationHeader);
			}
			
			// Process enable metrics property
			String enableMetricsProperty = serviceProperties.getProperty(ENABLE_METRICS_PROPERTY, "true");
			if (!enableMetricsProperty.equals("true") && !enableMetricsProperty.equals("false")) {
				logger.error("Error, invalid property value for " + ENABLE_METRICS_PROPERTY + ", value=" + enableMetricsProperty + ", must be true or false");
				throw new IllegalArgumentException("Invalid property value for " + ENABLE_METRICS_PROPERTY +
						", value=" + enableMetricsProperty + ", must be true or false");
			}
			logger.info("Property: " + ENABLE_METRICS_PROPERTY + "=" + enableMetricsProperty);
			enableMetrics = Boolean.parseBoolean(enableMetricsProperty);
			
		}
		catch(Throwable e) {
			logger.error("Error, context initialization failed.", e);
			setInitializationFailed(true);
		}
	}

	/**
	 * Set the servlet's context root in the service context for global access
	 *
	 * @param cr Context root of servlet
	 */
	public void setContextRoot(String cr) {
		contextRoot = cr;
	}

	/**
	 * Get the context root of this servlet
	 *
	 * @return Context root (e.g. /services/medical_insights)
	 */
	public String getContextRoot() {
		return contextRoot;
	}

	/**
	 * Allow direct access to the properties object
	 * @return
	 */
	public Properties getServiceProperties() {
		return serviceProperties;
	}

	/**
	 * Set the service version string
	 * @param externalVersion
	 */
	public void setBuildVersion(String buildVersion) {
		// Separate by possible snapshot hyphen
		// TODO May need to do more here once we know the version string format from the build
		Pattern pattern = Pattern.compile("([^-]*)(-.*)");
		Matcher match = pattern.matcher(buildVersion);
		buildVersionSuffix = "";
		if(match.matches()) {
			externalVersion = match.group(1);
			buildVersionSuffix = match.group(2);
		}
		else {
			externalVersion = buildVersion;
		}

		String versionParts[] = externalVersion.split("\\.");
		if (versionParts.length >= 1) {
			if (!versionParts[0].isEmpty()) {
				majorVersion = versionParts[0];
			}
		}
		if (versionParts.length >= 2) {
			minorVersion = versionParts[1];
		}
		if (versionParts.length >= 3) {
			fixVersion = versionParts[2];
		}
	}

	/**
	 * Get the service version string
	 * @return The version of the service provided by maven.
	 */
	public String getBuildVersion() {
		return externalVersion;
	}

	public String getMajorVersion() {
		return majorVersion;
	}

	public String getMinorVersion() {
		return minorVersion;
	}

	public String getFixVersion() {
		return fixVersion;
	}

	public String getBuildVersionSuffix() {
		return buildVersionSuffix;
	}

	/**
	 * Set the time of the build in zulu time.
	 * @param buildTime
	 */
	public void setBuildTime(String buildTime) {
		this.buildTime = buildTime;
	}

	/**
	 * Get the time of the build
	 * @return Time of build in zulu time.
	 */
	public String getBuildTime() {
		return buildTime;
	}

	public static long getRequestCount() {
		return requestCount.get();
	}

	public static void incrementRequestCount() {
		requestCount.incrementAndGet();
	}

	public static ServiceConcurrentLimit getConcurrentLimit() {
		return concurrentLimit;
	}

    public static String getBaseRelativePath(HttpServletRequest req, UriInfo uriInfo){
    	String datapowerUrl = req.getHeader(HEADER_X_WATSON_DP_URL_IN);
    	if(datapowerUrl!=null && !datapowerUrl.isEmpty()){
    		try {
    			URI baseUri = new URI(getDPScheme(datapowerUrl), getDPHost(datapowerUrl),
					uriInfo.getBaseUri().getPath(), uriInfo.getBaseUri().getFragment() );
    			return baseUri.getPath();
    		} catch (URISyntaxException e) {
    			//swallow exceptions and return default
    		} catch (NullPointerException e) {
    			StringBuffer sb = new StringBuffer();
    			Enumeration<String> headerNames = req.getHeaderNames();
    			while (headerNames.hasMoreElements()) {
    				String header = headerNames.nextElement();
    				if (sb.length() > 0) {
    					sb.append("; ");
    				}
    				sb.append(header + "=" + req.getHeader(header));
    			}
    			String baseUri = uriInfo.getBaseUri() != null ? uriInfo.getBaseUri().toASCIIString() : null;
    			logger.error("Exception occurred trying to parse datapowerUrl: " + datapowerUrl + "\nBase URI: " + baseUri + "\nHeaders: "  + sb.toString());
    			throw e;
    		}
    	}
    	//default to returning the baseUriBuilder from uriInfo if datapower header is not set
    	return uriInfo.getPath();
    }

    public static UriBuilder getBaseUriBuilder(HttpServletRequest req, UriInfo uriInfo){
    	String datapowerUrl = req.getHeader(HEADER_X_WATSON_DP_URL_IN);
    	if(datapowerUrl!=null && !datapowerUrl.isEmpty()){
    		try {
    			URI baseUri = new URI(getDPScheme(datapowerUrl), getDPHost(datapowerUrl),
					uriInfo.getBaseUri().getPath(), uriInfo.getBaseUri().getFragment() );
    			return UriBuilder.fromUri(baseUri);
    		} catch (URISyntaxException e) {
    			//swallow exceptions and return default
    		} catch (NullPointerException e) {
    			StringBuffer sb = new StringBuffer();
    			Enumeration<String> headerNames = req.getHeaderNames();
    			while (headerNames.hasMoreElements()) {
    				String header = headerNames.nextElement();
    				if (sb.length() > 0) {
    					sb.append("; ");
    				}
    				sb.append(header + "=" + req.getHeader(header));
    			}
    			String baseUri = uriInfo.getBaseUri() != null ? uriInfo.getBaseUri().toASCIIString() : null;
    			logger.error("Exception occurred trying to parse datapowerUrl: " + datapowerUrl + "\nBase URI: " + baseUri + "\nHeaders: "  + sb.toString());
    			throw e;
    		}
    	}
    	//default to returning the baseUriBuilder from uriInfo if datapower header is not set
    	return uriInfo.getBaseUriBuilder();
    }

	//precondition: datapowerUrl is not null or empty
	private static void parseDataPowerUrl(String datapowerUrl) throws URISyntaxException {
		//cache the host/scheme values
		if(!cachedDPUrl.contains(datapowerUrl)){
			URI dpUri;
			dpUri = new URI(datapowerUrl);
			Hashtable<String,String> dpMapping = new Hashtable<String,String>();
			String scheme = dpUri.getScheme();
			String host = dpUri.getHost();
			if(scheme!= null){
				dpMapping.put(SCHEMEKEY, scheme);
			}
			if(host!=null){
				dpMapping.put(HOSTKEY, host);
			}
			cachedDPUrl.put(datapowerUrl, dpMapping);
		}
	}
	//return null if there is no mapping
	private static String getDPHost(String datapowerUrl) throws URISyntaxException {
		parseDataPowerUrl(datapowerUrl);
		return cachedDPUrl.get(datapowerUrl).get(HOSTKEY);
	}
	//return null if there is no mapping
	private static String getDPScheme(String datapowerUrl) throws URISyntaxException {
		parseDataPowerUrl(datapowerUrl);
		return cachedDPUrl.get(datapowerUrl).get(SCHEMEKEY);
	}

    public static UriBuilder getRequestUriBuilder(HttpServletRequest req, UriInfo uriInfo){
    	String datapowerUrl = req.getHeader(HEADER_X_WATSON_DP_URL_IN);
    	if(datapowerUrl!=null && !datapowerUrl.isEmpty()){
    		try {
    			URI baseUri = new URI(getDPScheme(datapowerUrl), getDPHost(datapowerUrl), uriInfo.getRequestUri().getPath(), uriInfo.getRequestUri().getQuery(), uriInfo.getRequestUri().getFragment() );
    			return UriBuilder.fromUri(baseUri);
    		} catch (URISyntaxException e) {
    			//swallow exceptions and return default
    		}
    	}
    	//default to returning the requestUriBuilder from uriInfo if datapower header is not set
    	return uriInfo.getRequestUriBuilder();
    }

	public static boolean isDarkFeatureEnabled(String feature) {
		return enabledDarkFeatureSet == null || enabledDarkFeatureSet.contains(feature);
	}

	/**
	 * Get the service's authentication enum type
	 * @return service authentication type
	 */
	public AuthenticationType getAuthenticationType() {
		return authenticationType;
	}

	/**
	 * Get the service's custom authentication header name
	 * @return Authentication header name
	 */
	public String getAuthenticationHeaderName() {
		return authenticationHeader;
	}

	public synchronized boolean getInitializationFailed() {
		return initializationFailed;
	}

	public synchronized void setInitializationFailed(boolean initializationFailed) {
		this.initializationFailed = initializationFailed;
	}

	public ServiceMetrics getServiceMetrics() {
		return serviceMetrics;
	}

	public void setServiceMetrics(ServiceMetrics serviceMetrics) {
		ServiceContext.serviceMetrics = serviceMetrics;
	}
	
	public boolean isEnableMetrics() {
		return enableMetrics;
	}

}
