/*
 * (C) Copyright IBM Corp. 2015, 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.ibm.watson.common.service.base;

import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.core.Application;

public abstract class ServiceApplication extends Application {

	public ServiceApplication() {
		super();
		// Add initialization tasks here...
	}

	/**
	 * (non-Javadoc)
	 *
	 * @see javax.ws.rs.core.Application#getClasses()
	 */
	@Override
	public Set<Class<?>> getClasses() {
		Set<Class<?>> classes = new HashSet<Class<?>>();

		// Add handlers from this base project
		classes.add(ServiceExceptionMapper.class);
	    //classes.add(newclass.class);
		//classes.add(GZIPCompressInterceptor.class);
	    return classes;
	}

}
