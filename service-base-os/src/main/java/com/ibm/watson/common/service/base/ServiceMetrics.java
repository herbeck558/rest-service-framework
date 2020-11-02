/*
 * (C) Copyright IBM Corp. 2016, 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.watson.common.service.base;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public interface ServiceMetrics {

	public void filterEntry(HttpServletRequest request, HttpServletResponse response);
	
	public void filterExit(HttpServletRequest request, HttpServletResponse response, double apiTime);
	
}
