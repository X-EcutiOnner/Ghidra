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
package gui.event;

import static org.apache.commons.lang3.StringUtils.*;

import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import ghidra.util.Msg;

/**
 * A simple class that represents a mouse button and any modifiers needed to bind an action to a
 * mouse input event.
 * <P>
 * The modifiers used by this class will include the button down mask for the given button.  This
 * is done to match how {@link MouseEvent} uses its modifiers.
 */
public class MouseBinding {

	private static final Pattern BUTTON_PATTERN =
		Pattern.compile("button(\\d+)", Pattern.CASE_INSENSITIVE);

	private static final String SHIFT = "Shift";
	private static final String CTRL = "Ctrl";
	private static final String ALT = "Alt";
	private static final String META = "Meta";

	private int modifiers = -1;
	private int button = -1;

	/**
	 * Construct a binding with the given button number of the desired mouse button (e.g., 1, 2,...)
	 * @param button the button number
	 */
	public MouseBinding(int button) {
		this(button, -1);
	}

	/**
	 * Construct a binding with the given button number of the desired mouse button (e.g., 1, 2,...)
	 * as well as any desired modifiers (e.g., {@link InputEvent#SHIFT_DOWN_MASK}).
	 * @param button the button number
	 * @param modifiers the event modifiers
	 */
	public MouseBinding(int button, int modifiers) {
		this.button = button;

		// The button down mask is applied to the mouse event modifiers by Java. Thus, for us to
		// match the mouse event modifiers, we need to add the button down mask here.
		this.modifiers = InputEvent.getMaskForButton(button);

		if (modifiers > 0) {
			this.modifiers |= modifiers;
		}
	}

	/**
	 * The button used by this class
	 * @return the button used by this class
	 */
	public int getButton() {
		return button;
	}

	/**
	 * The modifiers used by this class
	 * @return the modifiers used by this class
	 */
	public int getModifiers() {
		return modifiers;
	}

	/**
	 * A user-friendly display string for this class
	 * @return a user-friendly display string for this class
	 */
	public String getDisplayText() {
		String modifiersText = InputEvent.getModifiersExText(modifiers);
		if (StringUtils.isBlank(modifiersText)) {
			// not sure if this can happen, since we add the button number to the modifiers
			return "Button" + button;
		}
		return modifiersText;
	}

	/**
	 * Create a mouse binding for the given event
	 * @param e the event
	 * @return the mouse binding
	 */
	public static MouseBinding getMouseBinding(MouseEvent e) {
		return new MouseBinding(e.getButton(), e.getModifiersEx());
	}

	/**
	 * Creates a mouse binding from the given string.  The string is expected to be of the form:
	 * {@code Ctrl+Button1}, which is the form of the text generated by {@link #getDisplayText()}.
	 *
	 * @param mouseString the mouse string
	 * @return the mouse binding or null if an invalid string was given
	 */
	public static MouseBinding getMouseBinding(String mouseString) {

		int button = getButton(mouseString);
		if (button == -1) {
			return null;
		}

		// be flexible on the tokens for splitting, even though '+' seems to be the standard
		StringTokenizer tokenizer = new StringTokenizer(mouseString, "- +");
		List<String> pieces = new ArrayList<>();
		while (tokenizer.hasMoreTokens()) {
			String token = tokenizer.nextToken();
			if (!pieces.contains(token)) {
				pieces.add(token);
			}
		}

		int modifiers = 0;
		for (Iterator<String> iterator = pieces.iterator(); iterator.hasNext();) {
			String piece = iterator.next();
			if (indexOfIgnoreCase(piece, SHIFT) != -1) {
				modifiers |= InputEvent.SHIFT_DOWN_MASK;
				iterator.remove();
			}
			else if (indexOfIgnoreCase(piece, CTRL) != -1) {
				modifiers |= InputEvent.CTRL_DOWN_MASK;
				iterator.remove();
			}
			else if (indexOfIgnoreCase(piece, ALT) != -1) {
				modifiers |= InputEvent.ALT_DOWN_MASK;
				iterator.remove();
			}
			else if (indexOfIgnoreCase(piece, META) != -1) {
				modifiers |= InputEvent.META_DOWN_MASK;
				iterator.remove();
			}
		}

		return new MouseBinding(button, modifiers);
	}

	private static int getButton(String mouseString) {

		Matcher buttonMatcher = BUTTON_PATTERN.matcher(mouseString);
		if (buttonMatcher.find()) {
			String numberString = buttonMatcher.group(1);
			try {
				int intValue = Integer.parseInt(numberString);
				if (intValue > 0) {
					return intValue;
				}
			}
			catch (NumberFormatException e) {
				Msg.error(MouseBinding.class, "Unable to parse button number %s in text %s"
						.formatted(numberString, mouseString));
			}
		}

		return -1;
	}

	/**
	 * Returns true if the given mouse event is the mouse released event for the mouse button used
	 * by this class.  This method will ignore modifier text, since modifiers can be pressed and
	 * released independent of the mouse button's release.
	 *
	 * @param e the event
	 * @return true if the given mouse event is the mouse released event for the mouse button used
	 * by this class
	 */
	public boolean isMatchingRelease(MouseEvent e) {

		int otherButton = e.getButton();
		if (button != otherButton) {
			return false;
		}

		int id = e.getID();
		if (id == MouseEvent.MOUSE_RELEASED || id == MouseEvent.MOUSE_CLICKED) {
			// not sure if released and clicked are sent for every OS / mouse combo
			return true;
		}

		return false;
	}

	@Override
	public String toString() {
		return getDisplayText();
	}

	@Override
	public int hashCode() {
		return Objects.hash(button, modifiers);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}

		MouseBinding other = (MouseBinding) obj;
		if (button != other.button) {
			return false;
		}
		if (modifiers != other.modifiers) {
			return false;
		}
		return true;
	}
}
