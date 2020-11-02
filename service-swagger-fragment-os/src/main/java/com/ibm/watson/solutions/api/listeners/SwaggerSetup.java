/*
 * (C) Copyright IBM Corp. 2001, 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.watson.solutions.api.listeners;

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.Path;

import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule;
import com.ibm.watson.solutions.api.rest.CustomSwaggerApiListingResource;
import com.ibm.watson.solutions.api.rest.CustomSwaggerApiListingResource2;

import io.swagger.jaxrs.config.BeanConfig;
import io.swagger.jaxrs.listing.ApiListingResource;
import io.swagger.jaxrs.listing.SwaggerSerializers;
import io.swagger.util.Json;

/**
 *
 * Helper methods to initialize the swagger environment
 *
 */
public class SwaggerSetup  {

	static{
        //register jaxb annotation module as secondary annotation handler
		JaxbAnnotationModule jaxbAnnotationModule = new JaxbAnnotationModule();
        jaxbAnnotationModule.setPriority(JaxbAnnotationModule.Priority.SECONDARY);
        Json.mapper().registerModule(jaxbAnnotationModule);
	}

	/**
	 * Return a list of REST classes needed for swagger support.
	 * These should then be added to the applications class set.
	 * @return Swagger API classes
	 */
	public static final List<Class<?>> getApplicationClassesForSwagger() {
		List<Class<?>> classes = new ArrayList<>();

		// Get Path annotation value from Swagger ApiListingResource class
		String pathValue = ApiListingResource.class.getAnnotation(Path.class).value();
		if(pathValue.equals("/")) {
			classes.add(CustomSwaggerApiListingResource.class);
		}
		else {
			classes.add(CustomSwaggerApiListingResource2.class);
		}
		classes.add(SwaggerSerializers.class);
		return classes;
	}

	/**
	 *	Initialize swagger bean
	 *  This is the "newer" way to initialize the swagger bean.
	 *
	 * @param title Swagger title
	 * @param description Swagger description
	 * @param version Version of the API
	 * @param apiBase Base path of the API
	 * @param swaggerPackages Packages to scan for REST APIs
	 * @param name of the dark feature filter class
	 */
	public static void initializeSwaggerBean(String title, String description, String version,
				String apiBase, String swaggerPackages, String darkFeatureFilterClassName) {
		// Swagger configuration
        BeanConfig beanConfig = new BeanConfig();
        beanConfig.setTitle(title);
        beanConfig.setDescription(description);
        beanConfig.setVersion(version);
        beanConfig.setBasePath(apiBase);
        beanConfig.setResourcePackage(swaggerPackages);
        beanConfig.setScan(true);
       	beanConfig.setFilterClass(darkFeatureFilterClassName);

	}

}
