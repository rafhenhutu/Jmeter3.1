/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */

package org.apache.jmeter.save;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;

import org.apache.jmeter.junit.JMeterTestCase;
import org.apache.jmeter.util.JMeterUtils;
import org.apache.jorphan.collections.HashTree;
import org.junit.Test;

public class TestSaveService extends JMeterTestCase {
    
    // testLoadAndSave test files
    private static final String[] FILES = new String[] {
        "AssertionTestPlan.jmx",
        "AuthManagerTestPlan.jmx",
        "HeaderManagerTestPlan.jmx",
        "InterleaveTestPlan2.jmx", 
        "InterleaveTestPlan.jmx",
        "LoopTestPlan.jmx",
        "Modification Manager.jmx",
        "OnceOnlyTestPlan.jmx",
        "proxy.jmx",
        "ProxyServerTestPlan.jmx",
        "SimpleTestPlan.jmx",
        "GuiTest.jmx", 
        "GuiTest231.jmx",
        // autogenerated test files
        "GenTest27.jmx", // 2.7
        "GenTest210.jmx", // 2.10
        "GenTest2_13.jmx", // 2.13
        "GenTest3_0.jmx", // 3.0
        };

    // Test files for testLoadAndSave; output will generally be different in size but same number of lines
    private static final String[] FILES_LINES = new String[] {
        "GuiTest231_original.jmx",
        "GenTest25.jmx", // GraphAccumVisualizer obsolete, BSFSamplerGui now a TestBean
        "GenTest251.jmx", // GraphAccumVisualizer obsolete, BSFSamplerGui now a TestBean
        "GenTest26.jmx", // GraphAccumVisualizer now obsolete
        "GenTest27_original.jmx", // CTT changed to use intProp for mode
    };

    // Test files for testLoad; output will generally be different in size and line count
    private static final String[] FILES_LOAD_ONLY = new String[] {
        "GuiTest_original.jmx", 
        "GenTest22.jmx",
        "GenTest231.jmx",
        "GenTest24.jmx",
        };

    private static final boolean saveOut = JMeterUtils.getPropDefault("testsaveservice.saveout", false);


    @Test
    public void testPropfile1() throws Exception {
        assertEquals("Property Version mismatch, ensure you update SaveService#PROPVERSION field with _version property value from saveservice.properties", SaveService.PROPVERSION, SaveService.getPropertyVersion());            
    }

    @Test
    public void testPropfile2() throws Exception {
        assertEquals("Property File Version mismatch, ensure you update SaveService#FILEVERSION field with sha1 of saveservice.properties without newline", SaveService.FILEVERSION, SaveService.getFileVersion());
    }
    
    @Test
    public void testVersions() throws Exception {
        assertTrue("Unexpected version found", SaveService.checkVersions());
    }

    @Test
    public void testLoadAndSave() throws Exception {
        boolean failed = false; // Did a test fail?

        for (final String fileName : FILES) {
            final File testFile = findTestFile("testfiles/" + fileName);
            final File savedFile = findTestFile("testfiles/Saved" + fileName);
            failed |= loadAndSave(testFile, fileName, true, savedFile);
        }
        for (final String fileName : FILES_LINES) {
            final File testFile = findTestFile("testfiles/" + fileName);
            final File savedFile = findTestFile("testfiles/Saved" + fileName);
            failed |= loadAndSave(testFile, fileName, false, savedFile);
        }
        if (failed) // TODO make these separate tests?
        {
            fail("One or more failures detected");
        }
    }

    private boolean loadAndSave(File testFile, String fileName, boolean checkSize, File savedFile) throws Exception {
        
        boolean failed = false;

        final FileStats origStats = getFileStats(testFile);
        final FileStats savedStats = getFileStats(savedFile);

        ByteArrayOutputStream out = new ByteArrayOutputStream(1000000);
        try {
            HashTree tree = SaveService.loadTree(testFile);
            SaveService.saveTree(tree, out);
        } finally {
            out.close(); // Make sure all the data is flushed out
        }

        final FileStats compareStats = savedStats == FileStats.NO_STATS ? origStats : savedStats;

        final FileStats outputStats;
        try (ByteArrayInputStream ins = new ByteArrayInputStream(out.toByteArray());
                Reader insReader = new InputStreamReader(ins);
                BufferedReader bufferedReader = new BufferedReader(insReader)) {
            outputStats = computeFileStats(bufferedReader);
        }
        // We only check the length of the result. Comparing the
        // actual result (out.toByteArray==original) will usually
        // fail, because the order of the properties within each
        // test element may change. Comparing the lengths should be
        // enough to detect most problem cases...
        if ((checkSize && !compareStats.isSameSize(outputStats)) || !compareStats.hasSameLinesCount(outputStats)) {
            failed = true;
            System.out.println();
            System.out.println("Loading file testfiles/" + fileName + " and "
                    + "saving it back changes its size from " + compareStats.size + " to " + outputStats.size + ".");
            if (!origStats.hasSameLinesCount(outputStats)) {
                System.out.println("Number of lines changes from " + compareStats.lines + " to " + outputStats.lines);
            }
            if (saveOut) {
                final File outFile = findTestFile("testfiles/" + fileName + ".out");
                System.out.println("Write " + outFile);
                try (FileOutputStream outf = new FileOutputStream(outFile)) {
                    outf.write(out.toByteArray());
                }
                System.out.println("Wrote " + outFile);
            }
        }

        // Note this test will fail if a property is added or
        // removed to any of the components used in the test
        // files. The way to solve this is to appropriately change
        // the test file.
        return failed;
    }

    private FileStats getFileStats(File testFile) throws IOException,
            FileNotFoundException {
        if (testFile == null || !testFile.exists()) {
            return FileStats.NO_STATS;
        }
        try (FileReader fileReader = new FileReader(testFile);
                BufferedReader bufferedReader = new BufferedReader(fileReader)) {
            return computeFileStats(bufferedReader);
        }
    }

    /**
     * Calculate size and line count ignoring EOL and 
     * "jmeterTestPlan" element which may vary because of 
     * different attributes/attribute lengths.
     */
    private FileStats computeFileStats(BufferedReader br) throws IOException {
        int length = 0;
        int lines = 0;
        String line;
        while ((line = br.readLine()) != null) {
            lines++;
            if (!line.startsWith("<jmeterTestPlan")) {
                length += line.length();
            }
        }
        return new FileStats(length, lines);
    }

    @Test
    public void testLoad() throws Exception {
        for (String fileName : FILES_LOAD_ONLY) {
            File file = findTestFile("testfiles/" + fileName);
            try {
                HashTree tree = SaveService.loadTree(file);
                assertNotNull(tree);
            } catch (IllegalArgumentException ex) {
                fail("Exception loading " + file.getAbsolutePath());
            }
        }
    }

    @Test
    public void testClasses(){
        List<String> missingClasses = SaveService.checkClasses();
        if (missingClasses.size() > 0) {
            fail("One or more classes not found:"+missingClasses);
        }
    }

    private static class FileStats {
        int size;
        int lines;

        final static FileStats NO_STATS = new FileStats(-1, -1);

        public FileStats(int size, int lines) {
            this.size = size;
            this.lines = lines;
        }

        public boolean isSameSize(FileStats other) {
            if (other == null) {
                return false;
            }
            return size == other.size;
        }

        public boolean hasSameLinesCount(FileStats other) {
            if (other == null) {
                return false;
            }
            return lines == other.lines;
        }
    }
}
