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
package ghidra.app.plugin.core.symtable;

import java.awt.Component;

import javax.swing.*;

import docking.DockingUtils;
import docking.UndoRedoKeeper;
import ghidra.program.model.symbol.Symbol;

public class SymbolEditor extends DefaultCellEditor {

	private JTextField symbolField = null;
	private UndoRedoKeeper undoRedoKeeper;

	public SymbolEditor() {
		super(new JTextField());
		symbolField = (JTextField) super.getComponent();
		symbolField.setBorder(BorderFactory.createEmptyBorder());
		undoRedoKeeper = DockingUtils.installUndoRedo(symbolField);
	}

	@Override
	public boolean stopCellEditing() {
		if (super.stopCellEditing()) {
			undoRedoKeeper.clear();
			return true;
		}
		return false;
	}

	@Override
	public void cancelCellEditing() {
		super.cancelCellEditing();
		undoRedoKeeper.clear();
	}

	@Override
	public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected,
			int row, int column) {

		Symbol symbol = (Symbol) value;
		if (symbol != null && !symbol.isDeleted()) {
			symbolField.setText(symbol.getName());
		}
		else {
			symbolField.setText("");
		}
		return symbolField;
	}
}
