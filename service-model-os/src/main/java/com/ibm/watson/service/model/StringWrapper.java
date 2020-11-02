/*
 * (C) Copyright IBM Corp. 2015, 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.watson.service.model;

/**
 * Concrete String wrapper class used with JSON serialization.
 * This non-generic implementation is necessary for the Swagger @ApiOperation response=
 * parameter.  Swagger cannot process generic types.
 *
 */
public class StringWrapper extends GenericWrapper<String> {
	public StringWrapper(){
		super();
	}

	public StringWrapper(String str) {
		super(str);
	}
}
