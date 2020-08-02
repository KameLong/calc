package com.kamelong.Kdia

import javafx.scene.control.TextArea
import javafx.scene.layout.AnchorPane
import tornadofx.*

class MainView : View("Hello TornadoFX") {
    override val root: AnchorPane by fxml("/fxml/main.fxml")

    private val textArea: TextArea by fxid("sampleTextArea")

    init {
        this.primaryStage.isMaximized=true
    }
}