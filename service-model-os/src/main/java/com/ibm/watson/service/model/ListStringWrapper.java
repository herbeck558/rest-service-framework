/*
 * (C) Copyright IBM Corp. 2015, 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.watson.service.model;

import java.util.List;

/**
 * Concrete List<String> wrapper class used with JSON serialization.
 * This non-generic implementation is necessary for the Swagger @ApiOperation response=
 * parameter.  Swagger cannot process generic types.
 *
 */
public class ListStringWrapper extends GenericWrapper<List<String>> {

	public ListStringWrapper(){
		super();
	}

	public ListStringWrapper(List<String> list) {
		super(list);
	}
}
