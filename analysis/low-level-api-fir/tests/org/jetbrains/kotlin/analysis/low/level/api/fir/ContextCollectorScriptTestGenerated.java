/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.low.level.api.fir;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.kotlin.test.util.KtTestUtil;
import org.jetbrains.kotlin.test.TestMetadata;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.regex.Pattern;

/** This class is generated by {@link org.jetbrains.kotlin.generators.tests.analysis.api.GenerateAnalysisApiTestsKt}. DO NOT MODIFY MANUALLY */
@SuppressWarnings("all")
@TestMetadata("analysis/low-level-api-fir/testdata/contextCollector")
@TestDataPath("$PROJECT_ROOT")
public class ContextCollectorScriptTestGenerated extends AbstractContextCollectorScriptTest {
    @Test
    public void testAllFilesPresentInContextCollector() throws Exception {
        KtTestUtil.assertAllTestsPresentByMetadataWithExcluded(this.getClass(), new File("analysis/low-level-api-fir/testdata/contextCollector"), Pattern.compile("^(.+)\\.(kts)$"), null, true);
    }

    @Nested
    @TestMetadata("analysis/low-level-api-fir/testdata/contextCollector/classHeaderPositions")
    @TestDataPath("$PROJECT_ROOT")
    public class ClassHeaderPositions {
        @Test
        public void testAllFilesPresentInClassHeaderPositions() throws Exception {
            KtTestUtil.assertAllTestsPresentByMetadataWithExcluded(this.getClass(), new File("analysis/low-level-api-fir/testdata/contextCollector/classHeaderPositions"), Pattern.compile("^(.+)\\.(kts)$"), null, true);
        }
    }

    @Nested
    @TestMetadata("analysis/low-level-api-fir/testdata/contextCollector/scripts")
    @TestDataPath("$PROJECT_ROOT")
    public class Scripts {
        @Test
        public void testAllFilesPresentInScripts() throws Exception {
            KtTestUtil.assertAllTestsPresentByMetadataWithExcluded(this.getClass(), new File("analysis/low-level-api-fir/testdata/contextCollector/scripts"), Pattern.compile("^(.+)\\.(kts)$"), null, true);
        }

        @Test
        @TestMetadata("insideClassMember.kts")
        public void testInsideClassMember() throws Exception {
            runTest("analysis/low-level-api-fir/testdata/contextCollector/scripts/insideClassMember.kts");
        }

        @Test
        @TestMetadata("insideTopLevelFunction.kts")
        public void testInsideTopLevelFunction() throws Exception {
            runTest("analysis/low-level-api-fir/testdata/contextCollector/scripts/insideTopLevelFunction.kts");
        }

        @Test
        @TestMetadata("topLevel.kts")
        public void testTopLevel() throws Exception {
            runTest("analysis/low-level-api-fir/testdata/contextCollector/scripts/topLevel.kts");
        }
    }

    @Nested
    @TestMetadata("analysis/low-level-api-fir/testdata/contextCollector/smartCasts")
    @TestDataPath("$PROJECT_ROOT")
    public class SmartCasts {
        @Test
        public void testAllFilesPresentInSmartCasts() throws Exception {
            KtTestUtil.assertAllTestsPresentByMetadataWithExcluded(this.getClass(), new File("analysis/low-level-api-fir/testdata/contextCollector/smartCasts"), Pattern.compile("^(.+)\\.(kts)$"), null, true);
        }
    }
}
