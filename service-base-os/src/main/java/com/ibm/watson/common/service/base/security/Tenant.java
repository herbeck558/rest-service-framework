/*
 * (C) Copyright IBM Corp. 2016, 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */


package com.ibm.watson.common.service.base.security;

/**
 * Class that identifies the client organization to which a user of the system belongs.
 */
public interface Tenant {

	public String getTenantId();
	public String getUserId();
}
