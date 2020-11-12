/*
 * (C) Copyright IBM Corp. 2016, 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.watson.common.service.base.security;

import org.slf4j.MDC;

/**
 * Class required in order to pass values from TenantManager thread local to child threads
 *
 * public static void MainTest(String [] args) {
 *      TenantManager.setTenant(new TenantImpl("blah", "blah"));
 *
 *      Thread t = new Thread(new Runnable() {
 *      	TenantManagerCopier tm = new TenantManagerCopier();
 *      	@Override
 *      	public void run() {
 *      		tm.recreateTenantManager();
 *              System.out.println("child tenant=" + TenantManager.getTenant().getTenantId());
 *          }
 *      });
 *      t.start();
 * }
 *
 * class TenantImpl implements Tenant {
 * 	  private String tenantId_;
 *    private String userId_;
 *
 *    protected TenantImpl(String tenantId, String userId) {
 *  	tenantId_ = tenantId;
 *  	userId_ = userId;
 *    }
 *
 *    @Override
 *    public String getTenantId() {
 *  	return tenantId_;
 *    }
 *
 *    @Override
 *    public String getUserId() {
 *  	return userId_;
 *    }
 *  }
 *
 */
public class TenantManagerCopier {
	private Tenant tenant;


	public TenantManagerCopier (){
		tenant = TenantManager.getTenant();
	}

	public void recrateTenantManager() {
		TenantManager.setTenant(tenant);

		// Clean up from previous thread use
		MDC.remove("tenantId");
		MDC.remove("userId");
		
		// Update the MDC tenantId and userId on this thread so it gets included in the log
		if(tenant != null) {
			if(tenant.getTenantId() != null) {
				MDC.put("tenantId", tenant.getTenantId()); // Hard coded key - Defined in MainServletFilter
			}

			if (tenant.getUserId() != null) {
				MDC.put("userId", tenant.getUserId()); // Hard coded key - Defined in MainServletFilter
			}
		}
	}


}



