/* ###
 * IP: GHIDRA
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package docking.widgets.filechooser;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;

import org.junit.Test;

import generic.test.AbstractGenericTest;

public class NativeMacFileDialogTest extends AbstractGenericTest {

	@Test
	public void testSaveDialogDetectedFromButtonText() {
		assertTrue(NativeMacFileDialog.isSaveDialog("Choose File", "Save As"));
		assertTrue(NativeMacFileDialog.isSaveDialog("Choose File", "Export"));
		assertTrue(NativeMacFileDialog.isSaveDialog("Choose File", "Create Archive"));
		assertTrue(NativeMacFileDialog.isSaveDialog("Choose File", "Select Output File"));
	}

	@Test
	public void testSaveDialogDetectedFromTitle() {
		assertTrue(NativeMacFileDialog.isSaveDialog("Export Trace", "OK"));
		assertTrue(NativeMacFileDialog.isSaveDialog("Choose Save Archive File", "OK"));
	}

	@Test
	public void testOpenDialogByDefault() {
		assertFalse(NativeMacFileDialog.isSaveDialog("Select File To Import", "Select File To Import"));
		assertFalse(NativeMacFileDialog.isSaveDialog("Open DataType Archive File", "Open"));
		assertFalse(NativeMacFileDialog.isSaveDialog("Add Jar File", "Choose"));
		assertFalse(NativeMacFileDialog.isSaveDialog(null, null));
	}

	@Test
	public void testSaveKeywordsMatchWholeWordsOnly() {
		// "Creates" and "exported" are not the verbs we are looking for
		assertFalse(NativeMacFileDialog.isSaveDialog("Open Exported Data", "Open"));
		assertFalse(NativeMacFileDialog.isSaveDialog("Select Creator", "OK"));
	}

	@Test
	public void testFilesAndDirectoriesModeNotSupported() {
		assertTrue(NativeMacFileDialog.supportsMode(GhidraFileChooserMode.FILES_ONLY));
		assertTrue(NativeMacFileDialog.supportsMode(GhidraFileChooserMode.DIRECTORIES_ONLY));
		assertFalse(NativeMacFileDialog.supportsMode(GhidraFileChooserMode.FILES_AND_DIRECTORIES));
	}

	@Test
	public void testNotUsedInTestingMode() {
		// the test framework drives the Swing chooser directly, so it must never be replaced
		assertFalse(NativeMacFileDialog.isAvailable(GhidraFileChooserMode.FILES_ONLY));
	}

	@Test
	public void testStartDirectoryPrefersSelectedFileParent() throws IOException {
		File dir = createTempDirectory("native.chooser");
		File file = new File(dir, "foo.gzf");
		File other = new File(dir, "other");
		assertTrue(other.mkdir());

		assertEquals(dir, NativeMacFileDialog.getStartDirectory(file, other));
		assertEquals(other, NativeMacFileDialog.getStartDirectory(null, other));
	}

	@Test
	public void testStartDirectoryFallsBackToHome() {
		File home = new File(System.getProperty("user.home"));
		File missing = new File("/no/such/directory/anywhere");

		assertEquals(home, NativeMacFileDialog.getStartDirectory(null, null));
		assertEquals(home, NativeMacFileDialog.getStartDirectory(null, missing));
	}

	@Test
	public void testStartDirectoryForExistingDirectorySelection() throws IOException {
		File dir = createTempDirectory("native.chooser");

		// a selected directory is itself the place to start
		assertEquals(dir, NativeMacFileDialog.getStartDirectory(dir, null));
	}
}
