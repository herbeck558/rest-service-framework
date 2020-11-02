/*
 * (C) Copyright IBM Corp. 2001, 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.watson.common.service.base.vt;

import org.junit.experimental.categories.Categories;
import org.junit.runner.RunWith;
import org.junit.runners.Suite.SuiteClasses;

import com.ibm.watson.common.service.base.utilities.MVT;
import com.ibm.watson.common.service.base.utilities.UNDERCONSTRUCTION;

@RunWith(Categories.class)
@Categories.ExcludeCategory(UNDERCONSTRUCTION.class)
@Categories.IncludeCategory(MVT.class)
@SuiteClasses(AllTests.class)
public class MVTTests {
	// Testsuite to call all tests that are tagged as DVT , will be part of DVT suite
}
