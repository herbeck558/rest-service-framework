/*
 * (C) Copyright IBM Corp. 2001, 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.watson.common.service.base.vt;

import org.junit.experimental.categories.Categories;
import org.junit.runner.RunWith;
import org.junit.runners.Suite.SuiteClasses;

import com.ibm.watson.common.service.base.utilities.BVT;
import com.ibm.watson.common.service.base.utilities.DVT;
import com.ibm.watson.common.service.base.utilities.MVT;
import com.ibm.watson.common.service.base.utilities.UNDERCONSTRUCTION;

@RunWith(Categories.class)
@Categories.ExcludeCategory({UNDERCONSTRUCTION.class})
@Categories.IncludeCategory({BVT.class, DVT.class, MVT.class})
@SuiteClasses(AllTests.class)
public class BVTTests {
	// Testsuite to call all tests that will be part of BVT suite
}
