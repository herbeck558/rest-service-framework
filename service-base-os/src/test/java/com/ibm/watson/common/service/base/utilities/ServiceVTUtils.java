/*
 * (C) Copyright IBM Corp. 2001, 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.watson.common.service.base.utilities;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.config.DecoderConfig.decoderConfig;
import static com.jayway.restassured.config.RestAssuredConfig.newConfig;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runners.model.Statement;
import org.slf4j.Logger;

import com.ibm.watson.common.service.base.security.MainServletFilter;
import com.jayway.restassured.config.EncoderConfig;
import com.jayway.restassured.config.HttpClientConfig;
import com.jayway.restassured.config.RestAssuredConfig;
import com.jayway.restassured.config.SSLConfig;
import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.path.json.JsonPath;
import com.jayway.restassured.response.Header;
import com.jayway.restassured.response.Headers;
import com.jayway.restassured.response.ValidatableResponse;
import com.jayway.restassured.specification.RequestSpecification;

@SuppressWarnings("deprecation")
public abstract class ServiceVTUtils {

	// Date format for logging
	protected static String DATE_FORMAT = "yyyy-mm-dd'T'hh:mm:ss";

	// Connection timeout to use in seconds (null uses default)
	protected static Integer CONNECTION_TIMEOUT = null;

	// Socket timeout to use in seconds (null uses default)
	protected static Integer SOCKET_TIMEOUT = null;

	// Connection manager timeout to use in seconds (null uses default)
	protected static Integer CONNECTION_MANAGER_TIMEOUT = null;

	protected static final String expectedOutputFolder = "/"+ "expectedOutput";
	protected static final String automationInputFolder = "/"+ "automationInput";

	// Common HTTP headers
	protected static final String CORRELATION_ID_NAME = "x-correlation-id";
	protected static final String CORRELATION_ID_VALUE_PREFIX = "junit-";

	protected static final Header HEADER_TEXT = new Header("Accept", "text/plain");
	protected static final Header HEADER_JSON = new Header("Accept", "application/json");
	protected static final Header HEADER_XML = new Header("Accept", "application/xml");
	protected static final Header HEADER_HTML = new Header("Accept", "text/html");
	protected static final Header HEADER_OCTET_STREAM = new Header("Accept", "application/octet-stream");
	protected static final Header HEADER_ACCEPT_IDENTITY = new Header("Accept-Encoding", "identity");
	protected static final Header HEADER_CONTENT_IDENTITY = new Header("Content-Encoding", "identity");
	protected static final Header HEADER_CONTENT_JSON = new Header("Content-Type", "application/json");
	protected static final Header HEADER_CONTENT_OCTET = new Header("Content-Type", "application/octet-stream");
	protected static final Header HEADER_CONTENT_X = new Header("Content-Type", "application/x-www-form-urlencoded");

	// Common HTTP header groupings
	protected static final Headers HEADER_STANDARD_JSON = new Headers(HEADER_JSON);
	protected static final Headers HEADER_STANDARD_TEXT = new Headers(HEADER_TEXT);
	protected static final Headers HEADER_STANDARD_XML = new Headers(HEADER_XML);
	protected static final Headers HEADER_STANDARD_HTML = new Headers(HEADER_HTML);
	protected static final Headers HEADER_IDENTITY_HTML = new Headers(HEADER_HTML, HEADER_ACCEPT_IDENTITY,
			HEADER_CONTENT_IDENTITY);
	protected static final Headers HEADER_CONSUMES_JSON_STANDARD_JSON = new Headers(HEADER_CONTENT_JSON, HEADER_JSON);
	protected static final Headers HEADER_OCTET_TEST = new Headers(HEADER_CONTENT_JSON, HEADER_OCTET_STREAM);

	// Dark feature special cases
	protected static final String SERVICE_ENABLED_DARK_FEATURES_ALL = "all";
	protected static final String SERVICE_ENABLED_DARK_FEATURES_NONE = "none";

	// Micro service special cases
	protected static final String SERVICE_ENABLED_MICRO_SERVICE_ALL = "all";
	protected static final String SERVICE_ENABLED_MICRO_SERVICE_NONE = "none";

	// Environment special cases
	protected static final String ENV_IBM_CLOUD = "ibm_cloud";
	protected static final String ENV_OPENSHIFT = "openshift";

	// Tenant types
	protected static final String TENANT_TYPE_APIKEY = "apikey";

	protected static final String correlationUUID = UUID.randomUUID().toString(); // Common for session
	protected static AtomicLong requestCounter = new AtomicLong(0);

	// Service paths
	protected static final String STATUS_PATH = "/v1/status";
	protected static final String STATUS_HEALTH_CHECK_PATH = "/v1/status/health_check";
	protected static final String SWAGGER_PATH = "/swagger/swagger.json";
	protected static final String SWAGGER_UI_PATH = "/documentation/api";

	// Service parms
	protected static final String VERSION_PARM = "version";

	// IBM Cloud paths
	protected static final String RC_INSTANCE_PATH = "/v2/resource_instances";

	static {
		int connectionTimeout = -1;
		if (CONNECTION_TIMEOUT != null)
			connectionTimeout = CONNECTION_TIMEOUT * 1000;
		int socketTimeout = -1;
		if (SOCKET_TIMEOUT != null)
			socketTimeout = SOCKET_TIMEOUT * 1000;
		int connectionManagerTimeout = -1;
		if (CONNECTION_MANAGER_TIMEOUT != null)
			connectionManagerTimeout = CONNECTION_MANAGER_TIMEOUT * 1000;
		RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(connectionTimeout)
				.setConnectionRequestTimeout(connectionManagerTimeout).setSocketTimeout(socketTimeout).build();
		HttpClientBuilder.create().setDefaultRequestConfig(requestConfig).build();
	}

	/**
	 * Returns the log4j logger instance
	 * 
	 * @return Logger - the log4j logger for the concrete implementation class
	 */
	protected abstract Logger getLogger();

	/**
	 * Instantiate service locations
	 */
	protected static void instantiateServiceLocation() {
	}

	public class Retry implements TestRule {
		private int retryCount;

		public Retry(int retryCount) {
			this.retryCount = retryCount;
		}

		public Statement apply(Statement base, Description description) {
			return statement(base, description);
		}

		private Statement statement(final Statement base, final Description description) {
			return new Statement() {
				@Override
				public void evaluate() throws Throwable {
					Throwable caughtThrowable = null;

					// implement retry logic here
					for (int i = 0; i < retryCount; i++) {
						try {
							base.evaluate();
							return;
						} catch (Throwable t) {
							caughtThrowable = t;
							System.err.println(description.getDisplayName() + ": run " + (i + 1) + " failed");
							caughtThrowable.printStackTrace();
							if (t instanceof ConnectException) {
								System.err.println("Retrying due to ConnectionTimeoutException");
								System.out.println(description.getDisplayName() + (i + 1)
										+ ": retry due to Connection Timeout Exception");
							} else {
								throw caughtThrowable;
							}
						}
					}
					System.err.println(description.getDisplayName() + ": giving up after " + retryCount + " failures");
					throw caughtThrowable;
				}
			};
		}
	}

	@Rule
	public Retry retry = new Retry(3);

	@Before
	public void displayTestStartMarker() throws Exception {
		System.out.println("<<<<<<<<<<<<<<<<<<<<<<<<<<<<< Start of test:" + testName.getMethodName());
	}

	// @After
	// Do stuff here

	@Rule
	public TestName testName = new TestName();

	@Rule
	public TestWatcher watcher = new TestWatcher() {

		@Override
		protected void failed(Throwable e, Description description) {
			String reason = "";
			if (e != null) {
				reason = e.getMessage();
			}
			DateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT);
			String currentTime = dateFormat.format(Calendar.getInstance().getTime());
			System.out
					.println(currentTime + " >>>>>>>>>> Failed >>>>>>>>>>> End of test:  " + testName.getMethodName());
			System.out.println("Reason: " + reason.toString());
			// System.out.println("Stack Trace:");
			// e.printStackTrace(); // This goes to stderr
		}

		@Override
		protected void succeeded(Description description) {
			DateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT);
			String currentTime = dateFormat.format(Calendar.getInstance().getTime());
			System.out
					.println(currentTime + " >>>>>>>>>> Passed >>>>>>>>>>> End of test:  " + testName.getMethodName());
		}
	};

	/**
	 * Indicates if the extended class had debug enabled.
	 * 
	 * @return
	 */
	public boolean isDebugEnabled() {
		;
		return getLogger().isDebugEnabled();
	}

	/**
	 * Run all tests, both base and tests from the concrete implementation class
	 */

	public void runAllTests() {
		try {
			Result result = JUnitCore.runClasses(this.getClass());
			int passed = result.getRunCount() - result.getFailureCount() - result.getIgnoreCount();
			StringBuffer summary = new StringBuffer();
			summary.append("\n====================================================\n")
					.append("\nTest Summary for " + this.getClass().getCanonicalName())
					.append("\nTests Run: " + result.getRunCount()).append("\nPassed: " + passed)
					.append("\nFailed: " + result.getFailureCount()).append("\nIgnored: " + result.getIgnoreCount())
					.append("\n\n====================================================\n");
			getLogger().info(summary.toString());

			if (result.getFailureCount() > 0) {
				getLogger().error(result.getFailureCount() + " tests failed!");
				for (Failure failure : result.getFailures()) {
					getLogger().error(failure.getTestHeader() + " | " + failure.getDescription(),
							failure.getException());
				}
			}

		} catch (Exception e) {
			getLogger().error("an error occurred executing jUnit tests for Service Example", e);
		}
	}

	//
	// Helper methods below here
	//

	protected static ValidatableResponse runSuccessValidation(ValidatableResponse response, ContentType contentType,
			int statusCode) {

		return runSuccessValidation(response, contentType, statusCode, false);
	}

	protected static ValidatableResponse runSuccessValidation(ValidatableResponse response, ContentType contentType,
			int statusCode, boolean suppressBody) {

		// Force logging for debug purposes, too hard to rerun with different settings
		// in DVT environments
		if (suppressBody) {
			// Log everything except the body to save log space
			response.log().status();
			response.log().headers();
			response.log().cookies();
			System.out.println("body: (suppressed)");
		} else {
			response.log().all();
		}
		response.statusCode(statusCode);

		if (contentType != null) {
			response.contentType(contentType);
		}
		return response;
	}

	protected ValidatableResponse runErrorValidation(ValidatableResponse response, ContentType contentType,
			int statusCode) {

		return runErrorValidation(response, contentType, statusCode, false);
	}

	protected ValidatableResponse runErrorValidation(ValidatableResponse response, ContentType contentType,
			int statusCode, boolean suppressBody) {

		// Force logging for debug purposes, too hard to rerun with different settings
		// in DVT environments
		if (suppressBody) {
			// Log everything except the body to save log space
			response.log().status();
			response.log().headers();
			response.log().cookies();
			System.out.println("body: (suppressed)");
		} else {
			response.log().all();
		}

		response.statusCode(statusCode).body("message", not(nullValue())).body("message", not(equalTo("")));
		if (contentType != null) {
			response.contentType(contentType);
		}

		return response;
	}

	protected static RequestSpecification buildBaseRequest(Headers headers) {
		return buildBaseRequest(headers, null, null, false);
	}

	protected static RequestSpecification buildBaseRequest(Headers headers, HttpClientConfig httpClientConfig,
			EncoderConfig encoderConfig, boolean minimalLogging) {
		boolean disableDecoders = false;
		for (Header header : headers) {
			if (header == HEADER_ACCEPT_IDENTITY || header == HEADER_CONTENT_IDENTITY) {
				disableDecoders = true;
				break;
			}
		}

		RestAssuredConfig config = newConfig();
		if (httpClientConfig != null) {
			config = config.httpClient(httpClientConfig);
		}
		if (encoderConfig != null) {
			config = config.encoderConfig(encoderConfig);
		}
		if (disableDecoders) {
			config = config.decoderConfig(decoderConfig().with().noContentDecoders());
		}

		RequestSpecification rs = given();
		SSLConfig sslConfig = SSLConfig.sslConfig().allowAllHostnames().relaxedHTTPSValidation("TLSv1.2");
		try {
			if (getTrustStorePath() != null && new File(getTrustStorePath()).isFile() && getKeystorePath() != null
					&& new File(getKeystorePath()).isFile()) {
				String trustStoreType = getTrustStorePath().endsWith(".p12") ? "PKCS12" : "JKS";
				KeyStore trustStore = loadKeyStore(getTrustStorePath(), getTrustStorePassword(), trustStoreType);

				String keyStoreType = getKeystorePath().endsWith(".p12") ? "PKCS12" : "JKS";
				KeyStore keyStore = loadKeyStore(getKeystorePath(), getKeystorePassword(), keyStoreType);

				KeyManagerFactory keyManagerFactory = KeyManagerFactory
						.getInstance(KeyManagerFactory.getDefaultAlgorithm());
				keyManagerFactory.init(keyStore, getKeystorePassword().toCharArray());
				TrustManagerFactory trustManagerFactory = TrustManagerFactory
						.getInstance(TrustManagerFactory.getDefaultAlgorithm());
				trustManagerFactory.init(trustStore);
				SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
				sslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);
				SSLSocketFactory sslSocketFactory = new SSLSocketFactory(sslContext, new String[] { "TLSv1.2" },
						new String[] { "TLS_RSA_WITH_AES_128_CBC_SHA", "TLS_RSA_WITH_AES_128_GCM_SHA256",
								"TLS_RSA_WITH_AES_128_CBC_SHA256", "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256" },
						SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
				sslConfig = sslConfig.sslSocketFactory(sslSocketFactory);
			}

		} catch (KeyManagementException | NoSuchAlgorithmException | KeyStoreException | UnrecoverableKeyException e) {
			throw new RuntimeException(e);
		}
		config = config.sslConfig(sslConfig);

		rs.config(config);
		rs.headers(headers);

		if (minimalLogging) {
			// Log everything except the body to save on log space
			rs.log().method();
			rs.log().path();
		} else {
			// Force all logging for debug purposes, too hard to rerun with different
			// settings in DVT environments
			rs.log().all();
		}

		// Add in correlation ID header with request index and UUID
		String correlation_id_value = CORRELATION_ID_VALUE_PREFIX + Long.toString(requestCounter.incrementAndGet())
				+ "-" + correlationUUID;
		rs.header(new Header(CORRELATION_ID_NAME, correlation_id_value));

		String authHeader = getServiceAuthApikey();
		if (authHeader != null) {
			String encodedAuth = Base64.getEncoder().encodeToString(("apikey:" + authHeader).getBytes());
			rs.header(new Header("Authorization", "Basic " + encodedAuth));
		}

		// Add in the tenant header info with userid/orgid, if provided
		if (getServiceTenant() != null) {
			String tenantHeader = getServiceTenantHeader();
			// Prefix JUNIT_TENANT_PREFIX is keyed off of for not metering
			String junitTenant = MainServletFilter.JUNIT_TENANT_PREFIX + getServiceTenant();
			if ((tenantHeader != null) && !tenantHeader.isEmpty()) {
				// Only pass tenant is customer header
				rs.header(new Header(tenantHeader, junitTenant));
			} else {
				// Add two types of auth headers to support different service configurations
				rs.header(new Header(MainServletFilter.HEADER_DP_CLIENET_ID, junitTenant));
				rs.header(new Header(MainServletFilter.HEADER_WATSON_USER_INFO,
						MainServletFilter.HEADER_CLOUD_BLUEMIX_INSTANCE_ID + "=" + junitTenant));
			}

		}

		return rs;
	}

	protected String getJsonFromFile(String expectedOutputFile) {
		return getJsonFromFile(this.getClass(), expectedOutputFile);
	}

	protected String getJsonFromFile(Class<?> testclass, String expectedOutputFile) {
		String expFile = expectedOutputFolder + "/"+ expectedOutputFile;
		return readFileFromResource(testclass, expFile);
	}

	protected String getXMLFromFile(String expectedOutputFile) {
		return getXMLFromFile(this.getClass(), expectedOutputFile);
	}

	protected String getXMLFromFile(Class<?> testclass, String expectedOutputFile) {
		String expFile = expectedOutputFolder + "/"+ expectedOutputFile;
		return readFileFromResource(testclass, expFile);
	}

	protected static String getJsonFromFile(String folderType, String fname) {
		return getJsonFromFile(ServiceVTUtils.class, folderType, fname);
	}

	protected static String getJsonFromFile(Class<?> testclass, String folderType, String fname) {
		String fileIs = null;
		if (folderType.equalsIgnoreCase("expectedoutput")) {
			fileIs = expectedOutputFolder + "/"+ fname;
		} else {
			fileIs = automationInputFolder + "/"+ fname;
		}

		return readFileFromResource(testclass, fileIs);
	}

	protected String getXMLFromFile(String folderType, String fname) {
		return getXMLFromFile(this.getClass(), folderType, fname);
	}

	protected String getXMLFromFile(Class<?> testclass, String folderType, String fname) {
		String fileIs = null;
		if (folderType.equalsIgnoreCase("expectedoutput")) {
			fileIs = expectedOutputFolder + "/"+ fname;
		} else {
			fileIs = automationInputFolder + "/"+ fname;
		}

		return readFileFromResource(testclass, fileIs);
	}

	protected String getTextFromFile(Class<?> testclass, String folderType, String fname) {
		String fileIs = null;
		if (folderType.equalsIgnoreCase("expectedoutput")) {
			fileIs = expectedOutputFolder + "/"+ fname;
		} else {
			fileIs = automationInputFolder + "/"+ fname;
		}

		return readFileFromResource(testclass, fileIs, true);
	}

	private static String readFileFromResource(Class<?> testclass, String expFilename) {
		return readFileFromResource(testclass, expFilename, false);
	}

	private static String readFileFromResource(Class<?> testclass, String expFilename, boolean preserveNewlines) {
		StringBuffer jsonString = new StringBuffer();
		try {
			InputStream is = testclass.getResourceAsStream(expFilename);

			InputStreamReader isr = new InputStreamReader(is, "UTF-8");
			BufferedReader br = new BufferedReader(isr);
			while (br.ready()) {
				if (preserveNewlines && jsonString.length() > 0) {
					jsonString.append("\n");
				}
				jsonString.append(br.readLine());
			}
			br.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return jsonString.toString();

	}

	protected String getJsonInputFromFile(String automationInputFile) {
		return getJsonInputFromFile(this.getClass(), automationInputFile);
	}

	protected String getJsonInputFromFile(Class<?> testclass, String automationInputFile) {
		String inpFile = automationInputFolder + "/"+ automationInputFile;
		return readFileFromResource(testclass, inpFile);
	}

	protected static String getServiceProtocol() {
		if (isHttpActive()) {
			return "http";
		} else if (isHttpsActive()) {
			return "https";
		} else {
			// No ports are active.
			return null;
		}
	}

	protected static String getServiceHostname() {
		return System.getProperty("test.host");
	}

	protected static String getServicePort() {
		if (isHttpActive()) {
			return getServiceHttpPort();
		} else if (isHttpsActive()) {
			return getServiceHttpsPort();
		} else {
			// No ports are active.
			return null;
		}
	}

	protected static String getServiceHttpPort() {
		return System.getProperty("test.httpPort", "80");
	}

	public static boolean isHttpActive() {
		if (getServiceHttpPort().equals("-1")) {
			return false;
		}
		return true;
	}

	protected static String getServiceHttpsPort() {
		return System.getProperty("test.httpSslPort", "");
	}

	public static boolean isHttpsActive() {
		if (getServiceHttpsPort().equals("-1")) {
			return false;
		}
		return true;
	}

	protected static String getServiceContextRoot() {
		return System.getProperty("test.contextRoot");
	}

	protected static String getServiceVersion() {
		return System.getProperty("test.version", "v1");
	}

	protected static String getServiceTenant() {
		return System.getProperty("test.tenant");
	}

	protected static String getServiceTenantHeader() {
		return System.getProperty("test.tenantHeader", "");
	}

	protected static String getServiceAuthApikey() {
		return System.getProperty("test.authApikey", "");
	}

	/**
	 * Checks micro service name against list of enabled micro services from system
	 * property.
	 * 
	 * @return boolean - The micro service is enabled
	 */
	public static boolean isMicroServiceEnabled(String microService) {
		String enabledMicroServiceProperty = System.getProperty("test.enabledMicroServices",
				SERVICE_ENABLED_MICRO_SERVICE_ALL);
		if (enabledMicroServiceProperty.equalsIgnoreCase(SERVICE_ENABLED_MICRO_SERVICE_ALL)) {
			return true;
		}
		if (enabledMicroServiceProperty.equalsIgnoreCase(SERVICE_ENABLED_MICRO_SERVICE_NONE)) {
			return false;
		}
		Set<String> enabledMicroServiceSet = new HashSet<String>();
		String[] values = enabledMicroServiceProperty.split(",");
		enabledMicroServiceSet.addAll(Arrays.asList(values));
		return enabledMicroServiceSet.contains(microService);
	}

	/**
	 * Checks feature name against list of enabled dark features from system
	 * property.
	 * 
	 * @return boolean - The feature is enabled
	 */
	public static boolean isServiceDarkFeatureEnabled(String darkFeature) {
		String enabledDarkFeaturesProperty = System.getProperty("test.enabledDarkFeatures",
				SERVICE_ENABLED_DARK_FEATURES_NONE);
		if (enabledDarkFeaturesProperty.equalsIgnoreCase(SERVICE_ENABLED_DARK_FEATURES_ALL)) {
			return true;
		}
		if (enabledDarkFeaturesProperty.equalsIgnoreCase(SERVICE_ENABLED_DARK_FEATURES_NONE)) {
			return false;
		}
		Set<String> enabledDarkFeatureSet = new HashSet<String>();
		String[] values = enabledDarkFeaturesProperty.split(",");
		enabledDarkFeatureSet.addAll(Arrays.asList(values));
		return enabledDarkFeatureSet.contains(darkFeature);
	}

	public static boolean isEnvironment(String environmentName) {
		String environment = System.getProperty("test.environment");
		return environment != null && environment.contains(environmentName);
	}

	private static String getKeystorePath() {
		String keystorePath = System.getProperty("test.keystore.path");
		keystorePath = (keystorePath != null && !keystorePath.isEmpty()) ? keystorePath : null;
		return keystorePath;
	}

	private static String getKeystorePassword() {
		String keystorePassword = System.getProperty("test.keystore.password");
		keystorePassword = (keystorePassword != null && !keystorePassword.isEmpty()) ? keystorePassword : "wats0n";
		return keystorePassword;
	}

	private static String getTrustStorePath() {
		String truststorePath = System.getProperty("test.truststore.path");
		truststorePath = (truststorePath != null && !truststorePath.isEmpty()) ? truststorePath : null;
		return truststorePath;
	}

	private static String getTrustStorePassword() {
		String truststorePassword = System.getProperty("test.truststore.password");
		truststorePassword = (truststorePassword != null && !truststorePassword.isEmpty()) ? truststorePassword
				: "wats0n";
		return truststorePassword;
	}

	/**
	 * This should include only the context root and JAX-RS API path e.g.
	 * "/healthcare-services/service-example/api"
	 * 
	 * @return Sting - the API root
	 */
	protected static String getApiRoot() {
		return "/" + getServiceContextRoot() + "/api";
	}

	/**
	 * The URL base of the server. This will look for any active protocol to test
	 * (http or https). This method will return the fully qualified path to the api
	 * root, including host, port and protocol info. e.g.
	 * "http(s)://<host.domain:port>/healthcare-services/service-example/api"
	 * 
	 * @return Sting - the fully qualified urlBase
	 * @throws Exception
	 */
	protected static String getUrlBase() {
		if (isHttpActive()) {
			return getHttpUrlBase();
		} else if (isHttpsActive()) {
			return getHttpsUrlBase();
		} else {
			// No ports are active.
			return null;
		}
	}

	/**
	 * The URL base of the server. This will look for any active protocol to test
	 * (http or https). This method will return the fully qualified path to the api
	 * root, including host, port and protocol info. e.g.
	 * "http(s)://<host.domain:port>/healthcare-services/service-example"
	 * 
	 * @return Sting - the fully qualified urlBase
	 * @throws Exception
	 */
	protected static String getUrlRoot() {
		if (isHttpActive()) {
			return getHttpUrlRoot();
		} else if (isHttpsActive()) {
			return getHttpsUrlRoot();
		} else {
			// No ports are active.
			return null;
		}
	}

	/**
	 * The http URL base of the server. Unless the test specifically needs http, use
	 * getUrlBase instead. This method will return the fully qualified path to the
	 * api root, including host, port and protocol info. e.g.
	 * "http://<host.domain:port>/healthcare-services/service-example/api"
	 * 
	 * @return Sting - the fully qualified urlBase
	 */
	protected static String getHttpUrlBase() {
		if (isHttpActive()) {
			return "http://" + getServiceHostname() + ":" + getServiceHttpPort() + getApiRoot();
		}
		return null;
	}

	/**
	 * The http URL base of the server. Unless the test specifically needs http, use
	 * getUrlBase instead. This method will return the fully qualified path to the
	 * service root, including host, port and protocol info. e.g.
	 * "http://<host.domain:port>/healthcare-services/service-example"
	 * 
	 * @return Sting - the fully qualified httpBase
	 */
	protected static String getHttpUrlRoot() {
		if (isHttpActive()) {
			return "http://" + getServiceHostname() + ":" + getServiceHttpPort() + "/" + getServiceContextRoot();
		}
		return null;
	}

	/**
	 * The https URL base of the server. Unless the test specifically needs https,
	 * use getUrlBase instead. This method will return the fully qualified path to
	 * the api root, including host, port and protocol info. e.g.
	 * "https://<host.domain:port>/healthcare-services/service-example/api"
	 * 
	 * @return Sting - the fully qualified SSL URL base
	 */
	protected static String getHttpsUrlBase() {
		if (isHttpsActive()) {
			return "https://" + getServiceHostname() + ":" + getServiceHttpsPort() + getApiRoot();
		}
		return null;
	}

	/**
	 * The https URL base of the server. Unless the test specifically needs https,
	 * use getUrlBase instead. This method will return the fully qualified path to
	 * the service root, including host, port and protocol info. e.g.
	 * "https://<host.domain:port>/healthcare-services/service-example"
	 * 
	 * @return Sting - the fully qualified SSL HTTP base
	 */
	protected static String getHttpsUrlRoot() {
		if (isHttpsActive()) {
			return "https://" + getServiceHostname() + ":" + getServiceHttpsPort() + "/" + getServiceContextRoot();
		}
		return null;
	}

	/**
	 *
	 * @return String to contain the content from the deployment descriptor file.
	 *
	 */
	protected static String getDeploymentDescriptorContent() {
		String deploymentDescriptorFilePath = System.getProperty("test.ddfile.path");
		if (deploymentDescriptorFilePath == null) {
			return null;
		} else {
			return readFile(deploymentDescriptorFilePath);
		}
	}

	private static String readFile(String filename) {
		String jsonString = null;
		StringBuffer jsonStringBuffer = new StringBuffer();
		InputStream is = null;
		try {

			File rfile = new File(filename);
			if (rfile.isAbsolute())
				is = new FileInputStream(rfile);
			// else
			// is =
			// DefaultIVT.class.getClass().getClassLoader().getResourceAsStream(automationInputFolder
			// + "/"+
			// filename);

			InputStreamReader isr = new InputStreamReader(is, "UTF-8");
			BufferedReader br = new BufferedReader(isr);
			while (br.ready()) {
				jsonStringBuffer.append(br.readLine());
			}
			br.close();
			jsonString = jsonStringBuffer.toString();
		} catch (IOException | NullPointerException e) {
		}

		return jsonString;

	}

	/**
	 *
	 * @return List of all services names defined in the deployment descriptor file
	 *
	 */
	protected static List<String> getServicesFromDeploymentDescriptor() {

		List<String> list = new ArrayList<String>();
		list = JsonPath.from(getDeploymentDescriptorContent()).getList("services.name");

		return list;
	}

	/**
	 *
	 * @param servicename Name of the service listed in deployment descriptor file
	 * @param attribute   Specific attribute name of the service defined in
	 *                    deployment descriptor file
	 * @return value of the attribute for the service defined.
	 *
	 */
	protected static String getServiceAttributeFromDeploymentDescriptor(String servicename, String attribute) {

		List<Map<?, ?>> list = new ArrayList<Map<?, ?>>();
		list = JsonPath.from(getDeploymentDescriptorContent()).getList("services");
		String attrValIs = "";

		for (Map<?, ?> ss : list) {
			if (ss.get("name").toString().equalsIgnoreCase(servicename)) {
				attrValIs = ss.get(attribute).toString();
				break;
			}
		}

		return attrValIs;

	}

	/**
	 *
	 * @param servicename Name of the service listed in deployment descriptor file
	 * @return service URL defined per the service properties defined in deployment
	 *         descriptor file
	 * @throws MalformedURLException
	 */
	protected static String getServiceUrlBaseFromDeploymentDescriptor(String servicename) throws MalformedURLException {

		List<Map<?, ?>> list = new ArrayList<Map<?, ?>>();
		list = JsonPath.from(getDeploymentDescriptorContent()).getList("services");
		String httpUrlBase = "";

		for (Map<?, ?> ss : list) {
			// drill down to specific service of interest
			if (ss.get("name").toString().equalsIgnoreCase(servicename)) {
				String protocolIs = JsonPath.from(getDeploymentDescriptorContent()).get("protocol");
				int portIs = Integer.parseInt(ss.get("port").toString());
				String apiRootIs = ss.get("apiroot").toString();
				String versionIs = ss.get("version").toString();
				String hostIs = "";
				try {
					hostIs = ss.get("host").toString();
				} catch (NullPointerException ne) {
					// if there is no host specified for a service
					// falls back to pick global host as host name
					if (hostIs.equalsIgnoreCase("") || hostIs == null) {
						hostIs = JsonPath.from(getDeploymentDescriptorContent()).get("globalhost");
					}
				}

				URL servURL = new URL(protocolIs, hostIs, portIs, "/" + apiRootIs + "/" + versionIs);
				httpUrlBase = servURL.toString();
				break;
			}
		}

		return httpUrlBase;

	}

	protected static String getServiceUrlBaseFromDeploymentDescriptor(String... tags) throws MalformedURLException {

		List<Map<?, ?>> list = new ArrayList<Map<?, ?>>();
		list = JsonPath.from(getDeploymentDescriptorContent()).getList("services");
		String httpUrlBase = "";

		ArrayList<String> arrL = new ArrayList<String>();
		for (String tag : tags) {
			arrL.add(tag);
		}

		for (Map<?, ?> ss : list) {
			// drill down to specific service of interest
			@SuppressWarnings("unchecked")
			List<String> tagList = (List<String>) ss.get("tags");
			if (tagList.containsAll(arrL)) {
				String protocolIs = JsonPath.from(getDeploymentDescriptorContent()).get("protocol");
				int portIs = Integer.parseInt(ss.get("port").toString());
				String apiRootIs = ss.get("apiroot").toString();
				String versionIs = ss.get("version").toString();
				String hostIs = "";
				try {
					hostIs = ss.get("host").toString();
				} catch (NullPointerException ne) {
					// if there is no host specified for a service
					// falls back to pick global host as host name
					if (hostIs.equalsIgnoreCase("") || hostIs == null) {
						hostIs = JsonPath.from(getDeploymentDescriptorContent()).get("globalhost");
					}
				}

				URL servURL = new URL(protocolIs, hostIs, portIs, "/" + apiRootIs + "/" + versionIs);
				httpUrlBase = servURL.toString();
				break;
			}
		}

		return httpUrlBase;

	}

	private static KeyStore loadKeyStore(String path, String password, String storeType) {
		KeyStore keyStore;
		try {
			keyStore = KeyStore.getInstance(storeType);
			keyStore.load(new FileInputStream(path), password.toCharArray());
		} catch (Exception ex) {
			throw new RuntimeException("Error while extracting the keystore", ex);
		}
		return keyStore;
	}

	/**
	 * The URL of the IBM Cloud IAM identity token API
	 */
	protected static String getIamIdentityToken() {
		return "https://" + System.getProperty("test.iamHostname") + "/identity/token";
	}

	/**
	 * The URL of the IBM Cloud resource controller's resource instances
	 */
	protected static String getResourceControllerBase() {
		return "https://" + System.getProperty("test.rcHostname") + "/v2/resource_instances";
	}
}
