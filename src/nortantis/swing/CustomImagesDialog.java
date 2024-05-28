package nortantis.swing;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileSystemView;

import org.apache.commons.io.FileUtils;

import nortantis.IconType;
import nortantis.MapSettings;
import nortantis.editor.UserPreferences;
import nortantis.util.AssetsPath;
import nortantis.util.FileHelper;
import nortantis.util.Logger;

@SuppressWarnings("serial")
public class CustomImagesDialog extends JDialog
{
	private JTextField customImagesFolderField;

	public CustomImagesDialog(MainWindow mainWindow, String currentCustomImagesPath, Consumer<String> storeResult)
	{
		super(mainWindow, "Custom Images Folder", Dialog.ModalityType.APPLICATION_MODAL);
		setSize(new Dimension(840, 700));
		JPanel content = new JPanel();
		add(content);
		content.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		content.setLayout(new BorderLayout());

		int space = 6;

		GridBagOrganizer organizer = new GridBagOrganizer();
		content.add(organizer.panel, BorderLayout.CENTER);
		organizer.addLeftAlignedComponent(new JLabel(
				"<html>A custom images folder allows you to use your own images instead of Nortantis's built-in images for a map."
						+ " To do so, enter a path to a "
						+ "folder with your images. If the folder is empty, Nortantis will copy its installed images into it as a "
						+ "starting point. " + "The required folder structure is: </html>"),
				space, space, false);

		int spaceBetweenPaths = 2;
		organizer.addLeftAlignedComponent(new JLabel("<custom images folder>" + File.separator + "borders" + File.separator
				+ "<border type>" + File.separator + "<border images>"), space, spaceBetweenPaths, false);
		organizer.addLeftAlignedComponent(new JLabel("<custom images folder>" + File.separator + "cities"
				+ File.separator + "<city type>" + File.separator + "<city images>"), spaceBetweenPaths, spaceBetweenPaths, false);
		organizer.addLeftAlignedComponent(new JLabel("<custom images folder>" + File.separator + "decorations"
				+ File.separator + "<decoration type>" + File.separator + "<decoration images>"), spaceBetweenPaths, spaceBetweenPaths, false);
		organizer.addLeftAlignedComponent(new JLabel("<custom images folder>" + File.separator + "hills"
				+ File.separator + "<hill type>" + File.separator + "<hill images>"), spaceBetweenPaths, spaceBetweenPaths, false);
		organizer.addLeftAlignedComponent(new JLabel("<custom images folder>" + File.separator + "mountains"
				+ File.separator + "<mountain type>" + File.separator + "<mountain images>"), spaceBetweenPaths, spaceBetweenPaths, false);
		organizer.addLeftAlignedComponent(new JLabel("<custom images folder>" + File.separator + "sand"
				+ File.separator + "<dune type>" + File.separator + "<sand dune images>"), spaceBetweenPaths, spaceBetweenPaths, false);
		organizer.addLeftAlignedComponent(new JLabel("<custom images folder>" + File.separator + "trees"
				+ File.separator + "<tree type>" + File.separator + "<tree images>"), spaceBetweenPaths, spaceBetweenPaths, false);

		organizer.addLeftAlignedComponent(new JLabel("<html>The names above in angle brackets are folder and file names"
				+ " that you can configure to be whatever name you want. Folder names without angle brackets, however, must be exactly as described above or else Nortantis"
				+ " will ignore those folders. Images must be either PNG or" + " JPG format. PNG is recommended because it"
				+ " supports transparency and isn't lossy.</html>"), space, space, false);
		organizer.addLeftAlignedComponent(new JLabel("<html>Valid border image names are 'upper_left_corner', 'upper_right_corner', "
				+ "'lower_left_corner', 'lower_right_corner', 'top_edge', 'bottom_edge', 'left_edge', 'right_edge'. At least one corner and"
				+ " one edge must be given. If corners are wider than the sides of edges, the corners will be inset into the map.</html>"),
				space, space, false);

		organizer.addLeftAlignedComponent(new JLabel("<html>Regarding tree images, although the &lt;tree type&gt; folder can have any name,"
				+ " if you want new maps to use your tree type appropriately for the biomes the trees are placed in, then you must use the folder names 'cacti', 'deciduous',"
				+ " and 'pine'. If you don't want all three of those tree types,"
				+ " then simply don't include the folder or images for the type you don't want to use.</html>"), space, space, false);

		organizer.addLeftAlignedComponent(new JLabel(
				"<html>If you want new maps to add hills around mountains, then for each mountain type, create a hill type with the same name.</html>"),
				space, space, false);

		organizer
				.addLeftAlignedComponent(new JLabel("<html>After making changes to custom images, to get Nortantis to see those changes you"
						+ " can either close and re-open Nortantis or use " + mainWindow.getFileMenuName() + " -> "
						+ mainWindow.getRefreshImagesMenuName() + ".</html>"), space, space, false);
		organizer.addLeftAlignedComponent(
				new JLabel("<html>To revert back to using Nortantis's installed images, clear out the" + " field below.</html>"), space, 10,
				false);

		JButton openButton = new JButton("Open");

		customImagesFolderField = new JTextField();
		customImagesFolderField.getDocument().addDocumentListener(new DocumentListener()
		{

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				openButton.setEnabled(!customImagesFolderField.getText().isEmpty());
			}

			@Override
			public void insertUpdate(DocumentEvent e)
			{
				openButton.setEnabled(!customImagesFolderField.getText().isEmpty());
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				openButton.setEnabled(!customImagesFolderField.getText().isEmpty());
			}
		});
		customImagesFolderField.setText(FileHelper.replaceHomeFolderPlaceholder(currentCustomImagesPath));
		JButton browseButton = new JButton("Browse");
		browseButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				File folder = new File(customImagesFolderField.getText());
				if (!folder.exists())
				{
					folder = FileSystemView.getFileSystemView().getDefaultDirectory();
				}
				JFileChooser folderChooser = new JFileChooser(folder);
				folderChooser.setDialogTitle("Select a Folder");
				folderChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

				int returnValue = folderChooser.showOpenDialog(null);
				if (returnValue == JFileChooser.APPROVE_OPTION)
				{
					customImagesFolderField.setText(folderChooser.getSelectedFile().toString());
				}
			}
		});

		openButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				File folder = new File(customImagesFolderField.getText());
				if (!folder.exists())
				{
					JOptionPane.showMessageDialog(null, "Unable to open " + folder.getAbsolutePath() + ". The folder does not exist.",
							"Error", JOptionPane.ERROR_MESSAGE);
					return;
				}
				if (!folder.isDirectory())
				{
					JOptionPane.showMessageDialog(null,
							"Unable to open " + folder.getAbsolutePath() + ". That path is a file, not a folder.", "Error",
							JOptionPane.ERROR_MESSAGE);
					return;
				}

				if (Desktop.isDesktopSupported())
				{
					try
					{
						Desktop.getDesktop().open(folder);
					}
					catch (IOException ex)
					{
						ex.printStackTrace();
						Logger.printError("Error while trying to open custom images folder: ", ex);
					}
				}
			}
		});
		openButton.setEnabled(!customImagesFolderField.getText().isEmpty());

		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
		panel.add(new JLabel("Custom images folder:"));
		panel.add(Box.createHorizontalStrut(10));
		panel.add(customImagesFolderField);
		panel.add(Box.createHorizontalStrut(5));
		panel.add(browseButton);
		panel.add(Box.createHorizontalStrut(5));
		panel.add(openButton);

		organizer.addLeftAlignedComponent(panel, false);

		JCheckBox makeDefaultCheckbox = new JCheckBox("Make this the default for new random maps");
		organizer.addLeftAlignedComponent(makeDefaultCheckbox);


		organizer.addVerticalFillerRow();

		JPanel bottomPanel = new JPanel();
		content.add(bottomPanel, BorderLayout.SOUTH);
		bottomPanel.setLayout(new FlowLayout(FlowLayout.RIGHT));
		JButton okayButton = new JButton("<html><u>O</u>K</html>");
		okayButton.setMnemonic(KeyEvent.VK_O);
		okayButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				boolean isChanged = !Objects.equals(customImagesFolderField.getText(),
						FileHelper.replaceHomeFolderPlaceholder(currentCustomImagesPath));
				if (mergeInstalledImagesIntoCustomFolderIfEmpty(customImagesFolderField.getText()))
				{
					JOptionPane.showMessageDialog(null,
							"Installed images successfully copied into " + Paths.get(customImagesFolderField.getText()).toAbsolutePath(),
							"Success", JOptionPane.INFORMATION_MESSAGE);
				}
				else if (MapSettings.isOldCustomImagesFolderStructure(customImagesFolderField.getText()))
				{
					try
					{
						MapSettings.convertOldCustomImagesFolder(customImagesFolderField.getText());

						JOptionPane.showMessageDialog(null, "Your custom images folder has been automatically converted to the new structure.",
								"Custom Images Folder Converted", JOptionPane.INFORMATION_MESSAGE);
					}
					catch (IOException ex)
					{
						String errorMessage = "Error while restructuring custom images folder for " + customImagesFolderField.getText() + ": " + ex.getMessage();
						Logger.printError(errorMessage, ex);
						JOptionPane.showMessageDialog(null, errorMessage, "Error", JOptionPane.ERROR_MESSAGE);
					}
				}

				// If the custom images folder changed, then store the value, refresh images, and redraw the map.
				if (isChanged)
				{
					storeResult.accept(FileHelper.replaceHomeFolderWithPlaceholder(customImagesFolderField.getText()));
				}

				if (makeDefaultCheckbox.isSelected())
				{
					UserPreferences.getInstance().defaultCustomImagesPath = FileHelper
							.replaceHomeFolderWithPlaceholder(customImagesFolderField.getText());
				}

				dispose();
			}
		});
		bottomPanel.add(okayButton);

		JButton cancelButton = new JButton("<html><u>C</u>ancel</html>");
		cancelButton.setMnemonic(KeyEvent.VK_C);
		cancelButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				dispose();
			}
		});
		bottomPanel.add(cancelButton);
	}

	private boolean mergeInstalledImagesIntoCustomFolderIfEmpty(String customImagesFolder)
	{
		if (customImagesFolder == null || customImagesFolder.isEmpty())
		{
			return false;
		}

		File folder = new File(customImagesFolder);
		if (!folder.exists())
		{
			JOptionPane.showMessageDialog(null,
					"Unable to copy installed images into " + folder.getAbsolutePath() + ". The folder does not exist.", "Error",
					JOptionPane.ERROR_MESSAGE);
			return false;
		}
		else if (!folder.isDirectory())
		{
			JOptionPane.showMessageDialog(null,
					"Unable to copy installed images into " + folder.getAbsolutePath() + ". That path is a file, not a folder.", "Error",
					JOptionPane.ERROR_MESSAGE);
			return false;
		}

		boolean isFolderEmpty;
		try
		{
			isFolderEmpty = !Files.newDirectoryStream(folder.toPath()).iterator().hasNext();
		}
		catch (IOException ex)
		{
			JOptionPane.showMessageDialog(null, "Error while checking if " + folder.getAbsolutePath() + " is empty: " + ex.getMessage(),
					"Error", JOptionPane.ERROR_MESSAGE);
			return false;
		}

		try
		{
			if (isFolderEmpty)
			{
				FileUtils.copyDirectoryToDirectory(Paths.get(AssetsPath.getInstallPath(), "borders").toFile(), folder);
				for (IconType type : IconType.values())
				{
					FileUtils.copyDirectoryToDirectory(Paths.get(AssetsPath.getInstallPath(), type.toString()).toFile(), folder);					
				}
				return true;
			}
		}
		catch (IOException ex)
		{
			JOptionPane.showMessageDialog(null,
					"Error while copying installed images into " + folder.getAbsolutePath() + ": " + ex.getMessage(), "Error",
					JOptionPane.ERROR_MESSAGE);
		}

		return false;
	}
}
