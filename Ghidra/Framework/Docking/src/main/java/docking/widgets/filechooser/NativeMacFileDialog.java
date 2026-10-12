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

import java.awt.*;
import java.io.File;
import java.io.FilenameFilter;
import java.util.List;
import java.util.regex.Pattern;

import javax.swing.SwingUtilities;

import ghidra.framework.OperatingSystem;
import ghidra.framework.Platform;
import ghidra.util.SystemUtilities;

/**
 * Shows the native macOS open/save panel in place of the {@link GhidraFileChooser} Swing dialog.
 * <p>
 * The AWT {@link FileDialog} is backed by {@code NSOpenPanel} and {@code NSSavePanel} on macOS.
 * It can choose either files or directories, but not both, so
 * {@link GhidraFileChooserMode#FILES_AND_DIRECTORIES} keeps using the Swing chooser.
 */
class NativeMacFileDialog {

	/** macOS AWT property that switches the open panel to choosing directories */
	private static final String DIRECTORIES_PROPERTY = "apple.awt.fileDialogForDirectories";

	/**
	 * The chooser has no explicit open/save mode, so we infer it from the words used in the title
	 * and on the approve button.
	 */
	private static final Pattern SAVE_WORDS =
		Pattern.compile("\\b(save|export|create|output)\\b", Pattern.CASE_INSENSITIVE);

	private NativeMacFileDialog() {
		// utility class
	}

	/**
	 * Returns true if the native dialog should be used for a chooser in the given mode
	 * @param mode the chooser's file selection mode
	 * @return true if the native dialog should be used
	 */
	static boolean isAvailable(GhidraFileChooserMode mode) {
		if (Platform.CURRENT_PLATFORM.getOperatingSystem() != OperatingSystem.MAC_OS_X) {
			return false;
		}

		// tests drive the Swing chooser directly and cannot operate a native dialog
		if (SystemUtilities.isInTestingMode() || SystemUtilities.isInHeadlessMode() ||
			GraphicsEnvironment.isHeadless()) {
			return false;
		}

		return supportsMode(mode);
	}

	static boolean supportsMode(GhidraFileChooserMode mode) {
		return mode != GhidraFileChooserMode.FILES_AND_DIRECTORIES;
	}

	/**
	 * Returns true if a chooser with the given title and approve button text is used to pick a
	 * file to write, in which case the save panel is shown so the user can type a new name.
	 * @param title the chooser title
	 * @param approveButtonText the approve button text
	 * @return true if this looks like a save dialog
	 */
	static boolean isSaveDialog(String title, String approveButtonText) {
		String text = (title == null ? "" : title) + " " +
			(approveButtonText == null ? "" : approveButtonText);
		return SAVE_WORDS.matcher(text).find();
	}

	/**
	 * Picks the directory the dialog opens in: the directory of the selected file, if any, then
	 * the given current directory, then the user's home directory.
	 * @param selectedFile the initially selected file; may be null or not exist yet
	 * @param currentDirectory the chooser's current directory; may be null
	 * @return an existing directory
	 */
	static File getStartDirectory(File selectedFile, File currentDirectory) {
		if (selectedFile != null) {
			if (selectedFile.isDirectory()) {
				return selectedFile;
			}
			File parent = selectedFile.getParentFile();
			if (parent != null && parent.isDirectory()) {
				return parent;
			}
		}

		if (currentDirectory != null && currentDirectory.isDirectory()) {
			return currentDirectory;
		}

		return new File(System.getProperty("user.home"));
	}

	/**
	 * Shows the native dialog and waits for the user to close it.  Must be called on the Swing
	 * thread.
	 *
	 * @param parent the component the dialog is shown over; may be null
	 * @param title the dialog title
	 * @param mode the file selection mode; must be supported by {@link #supportsMode}
	 * @param save true to show the save panel; ignored when choosing directories
	 * @param multiSelect true to allow choosing more than one file
	 * @param selectedFile the initially selected file; may be null
	 * @param currentDirectory the chooser's current directory; may be null
	 * @param filter limits the files that may be chosen; may be null
	 * @return the chosen files; empty if the user cancelled
	 */
	static List<File> show(Component parent, String title, GhidraFileChooserMode mode,
			boolean save, boolean multiSelect, File selectedFile, File currentDirectory,
			FilenameFilter filter) {

		boolean directories = mode == GhidraFileChooserMode.DIRECTORIES_ONLY;
		boolean savePanel = save && !directories;

		FileDialog dialog = createDialog(parent, title);
		dialog.setMode(savePanel ? FileDialog.SAVE : FileDialog.LOAD);
		dialog.setMultipleMode(multiSelect && !savePanel);
		dialog.setDirectory(getStartDirectory(selectedFile, currentDirectory).getPath());
		if (selectedFile != null && !selectedFile.isDirectory()) {
			dialog.setFile(selectedFile.getName());
		}
		if (filter != null && !directories) {
			dialog.setFilenameFilter(filter);
		}

		String oldDirectoriesValue = System.getProperty(DIRECTORIES_PROPERTY);
		System.setProperty(DIRECTORIES_PROPERTY, Boolean.toString(directories));
		try {
			dialog.setVisible(true); // blocks until the user closes the dialog
		}
		finally {
			if (oldDirectoriesValue == null) {
				System.clearProperty(DIRECTORIES_PROPERTY);
			}
			else {
				System.setProperty(DIRECTORIES_PROPERTY, oldDirectoriesValue);
			}
			dialog.dispose();
		}

		if (dialog.getFile() == null) {
			return List.of(); // cancelled
		}

		if (dialog.isMultipleMode()) {
			return List.of(dialog.getFiles());
		}
		return List.of(new File(dialog.getDirectory(), dialog.getFile()));
	}

	private static FileDialog createDialog(Component parent, String title) {
		Window window = parent instanceof Window w ? w
				: parent == null ? null : SwingUtilities.getWindowAncestor(parent);
		if (window instanceof Dialog d) {
			return new FileDialog(d, title);
		}
		return new FileDialog(window instanceof Frame f ? f : (Frame) null, title);
	}
}
