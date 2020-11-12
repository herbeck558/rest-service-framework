/*
 * (C) Copyright IBM Corp. 2015, 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.watson.common.service.base;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ibm.watson.common.service.base.security.MainServletFilter;
import com.ibm.watson.solutions.api.listeners.SwaggerSetup;

/**
 * This base class uses the ServletContextLister class to do any startup time initialization
 * that is common across all services (e.g. swagger bean config).  Each service will extend
 * this class using the @WebListener annotation do any service-specific
 * initialization.
 *
 * The extending class must first call the setter methods in this class to pass in the
 * configuration details about the service.
 *
 */
public class ServiceBaseInitialization implements ServletContextListener {
	private static final Logger logger = LoggerFactory.getLogger(ServiceBaseInitialization.class.getName());

	private static long startTime = System.currentTimeMillis(); // Service start time. Used for up time calculation

	// These must be set by the extending class
	protected String buildVersion;
	protected String buildTime;
	protected String serviceTitle;
	protected String serviceDescription;
	protected String swaggerPackages;

	@Override
	public void contextDestroyed(ServletContextEvent event) {
		//no-op
	}

	/**
	 * Initialize swagger via the ServletContextEvent listener
	 */
	@Override
	public void contextInitialized(ServletContextEvent event) {

		String serviceVersion = buildVersion+" "+buildTime;
		String contextRoot = event.getServletContext().getContextPath();
		String apiBase = contextRoot+"/"+ServiceBaseConstants.SERVICE_API_ROOT;

		logger.info("Starting application: Context Root="+ contextRoot
				+ " version="+serviceVersion);

		// Set version and build time stamp in service context
		ServiceContext sc = ServiceContext.createInstance();
		sc.setBuildVersion(buildVersion);
		sc.setBuildTime(buildTime);
		sc.setContextRoot(contextRoot);

		// Register and set order of servlet filters
		ServletContext context = event.getServletContext();

		String uriPattern = "/"+ServiceBaseConstants.SERVICE_API_ROOT+"/*";
		context.addFilter("MainServletFilter", MainServletFilter.class).addMappingForUrlPatterns(null, false, uriPattern);

		// Initialize swagger
		SwaggerSetup.initializeSwaggerBean(
				serviceTitle,
				serviceDescription,
				serviceVersion,
				apiBase,
				swaggerPackages,
				DarkFeatureSwaggerFilter.class.getName());

				// Calling here to ensure initialization at startup when metering is enabled
//				if(ServiceMetering.getInstance().isEnabled()){
//					logger.info("Service metering is enabled for this environment");
//				}

	}

	public static long getStartTime() {
		return startTime;
	}

	public String getBuildVersion() {
		return buildVersion;
	}

	public void setBuildVersion(String buildVersion) {
		this.buildVersion = buildVersion;
	}

	public String getBuildTime() {
		return buildTime;
	}

	public void setBuildTime(String buildTime) {
		this.buildTime = buildTime;
	}

	public String getServiceTitle() {
		return serviceTitle;
	}

	public void setServiceTitle(String serviceTitle) {
		this.serviceTitle = serviceTitle;
	}

	public String getServiceDescription() {
		return serviceDescription;
	}

	public void setServiceDescription(String serviceDescription) {
		this.serviceDescription = serviceDescription;
	}

	public String getSwaggerPackages() {
		return swaggerPackages;
	}

	public void setSwaggerPackages(String swaggerPackages) {
		this.swaggerPackages = swaggerPackages;
	}

}