/*
 * (C) Copyright IBM Corp. 2016, 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */


package com.ibm.watson.common.service.base.security;

import java.util.HashMap;
import java.util.Map;

/**
 * This class is a wrapper class around an object to provide a "singleton" type instance on a per tenant basis.
 *
 * @param <T>
 */
public class TenantSingleton<T> {
	private Class<T> tClass = null;
	private Map<String,T> instances = new HashMap<String,T>();

	/**
	 * Constructor
	 * @param cls The class that this singleton will wrap.
	 */
	public TenantSingleton(Class<T> cls) {
		tClass = cls;
	}

	/**
	 * Get the per tenant singleton instance of this class.
	 *
	 * @return
	 * @throws Exception
	 */
    public synchronized T getInstance() throws Exception {
    	String tenantId = findTenantId();
    	if (!instances.containsKey(tenantId)) {
    		T instance = tClass.newInstance();
    		instances.put(tenantId, instance);
    	}
    	return instances.get(tenantId);
    }

    /**
     * Get the tenant ID
     * @return
     */
	protected static String findTenantId() {
		String tenantId = null;
		Tenant tenant = TenantManager.getTenant();
		if(tenant != null) {
			tenantId = tenant.getTenantId(); // Get organization ID
		}

		return tenantId;

	}

}
