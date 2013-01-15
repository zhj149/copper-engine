/*
 * Copyright 2002-2012 SCOOP Software GmbH
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
package de.scoopgmbh.copper.gui.ui.sql.result;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.scene.control.TableView;
import javafx.util.Callback;
import de.scoopgmbh.copper.gui.adapter.GuiCopperDataProvider;
import de.scoopgmbh.copper.gui.form.FxmlController;
import de.scoopgmbh.copper.gui.form.filter.FilterResultController;
import de.scoopgmbh.copper.gui.ui.sql.filter.SqlFilterModel;

public class SqlResultController implements Initializable, FilterResultController<SqlFilterModel,SqlResultModel>, FxmlController {
	private final GuiCopperDataProvider copperDataProvider;
	
	public SqlResultController(GuiCopperDataProvider copperDataProvider) {
		super();
		this.copperDataProvider = copperDataProvider;
	}

    @FXML //  fx:id="resultTable"
    private TableView<SqlResultModel> resultTable; // Value injected by FXMLLoader

    @Override // This method is called by the FXMLLoader when initialization is complete
    public void initialize(URL fxmlFileLocation, ResourceBundle resources) {
        assert resultTable != null : "fx:id=\"resultTable\" was not injected: check your FXML file 'WorkflowInstanceResult.fxml'.";
    }
	
	@Override
	public URL getFxmlRessource() {
		return getClass().getResource("SqlResult.fxml");
	}

	@Override
	public void showFilteredResult(List<SqlResultModel> filteredResult, SqlFilterModel usedFilter) {
		resultTable.getColumns().clear();
		
		if (!filteredResult.isEmpty()){
			for (int i=0;i<filteredResult.get(0).rows.size();i++){
				TableColumn<SqlResultModel, String> rowColumn = new TableColumn<SqlResultModel,String>();
				final int rowindex=i;
				rowColumn.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<SqlResultModel,String>, ObservableValue<String>>() {
					@Override
					public ObservableValue<String> call(CellDataFeatures<SqlResultModel, String> param) {
						return param.getValue().rows.get(rowindex);
					}
				});
				resultTable.getColumns().add(rowColumn);
			}
		}
		 resultTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
		
		ObservableList<SqlResultModel> content = FXCollections.observableList(new ArrayList<SqlResultModel>());;
		content.addAll(filteredResult);
		resultTable.setItems(content);
	}

	@Override
	public List<SqlResultModel> applyFilterInBackgroundThread(SqlFilterModel filter) {
		return copperDataProvider.executeSqlQuery(filter);
	}
	
	@Override
	public boolean canLimitResult() {
		return true;
	}

}