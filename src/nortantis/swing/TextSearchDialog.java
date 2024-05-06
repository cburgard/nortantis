package nortantis.swing;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import nortantis.MapText;
import nortantis.geom.Rectangle;
import nortantis.platform.awt.AwtFactory;

@SuppressWarnings("serial")
public class TextSearchDialog extends JDialog
{
	public JTextField searchField;
	private MainWindow mainWindow;
	private JButton searchForward;
	private JButton searchBackward;
	private JLabel notFoundLabel;

	// TODO Null out MainWindow.textSearchDialog when closing.

	public TextSearchDialog(MainWindow mainWindow)
	{
		super(mainWindow, "Search Text", Dialog.ModalityType.MODELESS);
		setSize(450, 70);
		setResizable(false);

		this.mainWindow = mainWindow;

		JPanel container = new JPanel();
		getContentPane().add(container);
		container.setLayout(new BoxLayout(container, BoxLayout.X_AXIS));
		final int padding = 4;
		container.setBorder(BorderFactory.createEmptyBorder(padding, padding, padding, padding));

		notFoundLabel = new JLabel("Not found");
		notFoundLabel.setForeground(new Color(255, 120, 120));
		notFoundLabel.setVisible(false);

		searchField = new JTextField();
		container.add(searchField);
		container.add(Box.createRigidArea(new Dimension(padding, 1)));
		searchField.getDocument().addDocumentListener(new DocumentListener()
		{
			public void changedUpdate(DocumentEvent e)
			{
				onSearchFieldChanged();
			}

			public void removeUpdate(DocumentEvent e)
			{
				onSearchFieldChanged();
			}

			public void insertUpdate(DocumentEvent e)
			{
				onSearchFieldChanged();
			}
		});

		container.add(notFoundLabel);
		container.add(Box.createRigidArea(new Dimension(padding, 1)));

		final int fontSize = 24;
		searchForward = new JButton("→");
		searchForward.setToolTipText("Search forward (enter key)");
		searchForward.setFont(new java.awt.Font(searchForward.getFont().getName(), searchForward.getFont().getStyle(), fontSize));
		getRootPane().setDefaultButton(searchForward);
		container.add(searchForward);
		container.add(Box.createRigidArea(new Dimension(padding, 1)));
		searchForward.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				search(true);
			}
		});

		searchBackward = new JButton("←");
		searchBackward.setToolTipText("Search backward");
		searchBackward.setFont(new java.awt.Font(searchBackward.getFont().getName(), searchBackward.getFont().getStyle(), fontSize));
		container.add(searchBackward);
		searchBackward.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				search(false);
			}
		});
	}

	private void onSearchFieldChanged()
	{
		notFoundLabel.setVisible(false);
		setSearchButtonsEnabled(!searchField.getText().isEmpty());
	}

	private void search(boolean isForward)
	{
		if (searchField.getText().isEmpty())
		{
			notFoundLabel.setVisible(false);
			return;
		}

		TextTool textTool = mainWindow.toolsPanel.getTextTool();
		MapText lastSelected = textTool.getTextBeingEdited();
		MapText searchResult = findNext(lastSelected, searchField.getText(), isForward);
		if (searchResult == null)
		{
			notFoundLabel.setVisible(true);
		}
		else
		{
			notFoundLabel.setVisible(false);
			if (mainWindow.toolsPanel.currentTool != textTool)
			{
				mainWindow.toolsPanel.handleToolSelected(textTool);
			}
			textTool.changeToEditModeAndSelectText(searchResult, false);

			if (searchResult.line1Area != null)
			{
				// Scroll to make the selected text visible
				
				// TODO Pad the rectangle to push the scroll to area more into the middle so it won't by default end up under the search box.
				
				Rectangle scrollTo = searchResult.line1Area.getBounds();
				if (searchResult.line2Area != null)
				{
					scrollTo = scrollTo.add(searchResult.line2Area.getBounds());
				}
				mainWindow.mapEditingPanel.scrollRectToVisible(AwtFactory.toAwtRectangle(scrollTo));
				
			}
		}
	}

	private MapText findNext(MapText start, String query, boolean isForward)
	{
		List<MapText> sorted = new ArrayList<>(
				mainWindow.edits.text.stream().filter(t -> t.value != null && !t.value.isEmpty()).collect(Collectors.toList()));
		sorted.sort((text1, text2) ->
		{
			if (text1.location == null && text2.location == null)
			{
				return 0;
			}
			else if (text1.location == null)
			{
				return -1;
			}
			else if (text2.location == null)
			{
				return 1;
			}
			return text1.location.compareTo(text2.location);
		});

		int i = start == null ? (isForward ? sorted.size() - 1 : 0) : sorted.indexOf(start);
		int count = 0;
		while (count < sorted.size())
		{
			if (isForward)
			{
				i++;
				if (i >= sorted.size())
				{
					i = 0;
				}
			}
			else
			{
				i--;
				if (i < 0)
				{
					i = sorted.size() - 1;
				}
			}

			if (sorted.get(i).value.toLowerCase().contains(query.toLowerCase()))
			{
				return sorted.get(i);
			}

			count++;
		}

		if (start != null && start.value.toLowerCase().contains(query.toLowerCase()))
		{
			return start;
		}
		return null;
	}

	boolean allowSearches = true;

	public void setAllowSearches(boolean allow)
	{
		this.allowSearches = allow;
		setSearchButtonsEnabled(allow);
	}

	private void setSearchButtonsEnabled(boolean enable)
	{
		searchForward.setEnabled(enable && allowSearches);
		searchBackward.setEnabled(enable && allowSearches);
	}
}
