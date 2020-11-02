/*
 * (C) Copyright IBM Corp. 2015, 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.ibm.watson.service.model;


/**
 * Generic POJO wrapper.  This class can be used wrap other simple types or
 * anonymous arrays and lists so that the generated JSON will have a
 * name/value structure.  For example, if you convert a List<String> object to
 * JSON, it will look like [ "string1", "string2" ].  Using this GenericWrapper class,
 * the JSON would look like { "value" : ["string1", "string2] }.
 * For a simple String object, it would look like this: { "value" : "string1" }
 * This wrapper will make the JSON output of your REST call more extensible.
 *
 * @param <T>
 */
public class GenericWrapper<T> {
	private T data;

	public GenericWrapper() {

	}

	public GenericWrapper(T result) {
		this.data = result ;
	}

	public void setData(T result) {
		this.data = result;
	}

	public T getData() {
		return data;
	}

}
