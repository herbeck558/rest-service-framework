/*
 * (C) Copyright IBM Corp. 2016, 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */


package com.ibm.watson.common.service.base.security;

/**
 * Class that provides tracking of the Tenant associated with the API
 * request being processed on the current Thread.
 */
public class TenantManager {

	private static final ThreadLocal<Tenant> tenantForThread_ = new ThreadLocal<>();


	public static Tenant getTenant() {
		return tenantForThread_.get();
	}

	// package visibility
	static void setTenant(Tenant t) {
		tenantForThread_.set(t);
	}
}
