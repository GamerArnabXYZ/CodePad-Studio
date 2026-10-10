package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.CodePadDatabase
import com.example.data.CodeProjectRepository
import com.example.ui.AutoWriteStudioScreen
import com.example.ui.CodePadStudioScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.StudioModule
import com.example.viewmodel.StudioViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CodePadStudioAppRoot()
        }
    }
}

@Composable
fun CodePadStudioAppRoot() {
    val context = LocalContext.current
    val repository = remember {
        val db = CodePadDatabase.getInstance(context)
        CodeProjectRepository(db.codeProjectDao())
    }
    val studioViewModel: StudioViewModel = viewModel(
        factory = StudioViewModel.provideFactory(repository)
    )
    val uiState by studioViewModel.uiState.collectAsStateWithLifecycle()

    BackHandler(enabled = uiState.isRecordingMode || uiState.activeModule == StudioModule.AUTO_WRITE_STUDIO) {
        if (uiState.isRecordingMode) {
            studioViewModel.handleTripleTapGesture()
        } else {
            studioViewModel.selectModule(StudioModule.CODEPAD_IDE)
        }
    }

    val showNavigationBar =
        !(uiState.activeModule == StudioModule.AUTO_WRITE_STUDIO && uiState.isRecordingMode)

    MyApplicationTheme(
        darkTheme = uiState.isDarkTheme,
        uiFontScale = uiState.uiFontScale
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (showNavigationBar) {
                    NavigationBar {
                        val isIde = uiState.activeModule == StudioModule.CODEPAD_IDE
                        NavigationBarItem(
                            selected = isIde,
                            onClick = { studioViewModel.selectModule(StudioModule.CODEPAD_IDE) },
                            icon = {
                                Icon(
                                    imageVector = if (isIde) Icons.Filled.Code else Icons.Outlined.Code,
                                    contentDescription = "CodePad Studio"
                                )
                            },
                            label = { Text("CodePad Studio") },
                            modifier = Modifier.testTag("nav_codepad_studio")
                        )

                        val isAutoWrite = uiState.activeModule == StudioModule.AUTO_WRITE_STUDIO
                        NavigationBarItem(
                            selected = isAutoWrite,
                            onClick = { studioViewModel.selectModule(StudioModule.AUTO_WRITE_STUDIO) },
                            icon = {
                                Icon(
                                    imageVector = if (isAutoWrite) {
                                        Icons.Filled.PlayCircle
                                    } else {
                                        Icons.Outlined.PlayCircleOutline
                                    },
                                    contentDescription = "Auto Write Studio"
                                )
                            },
                            label = { Text("Auto Write") },
                            modifier = Modifier.testTag("nav_autowrite_studio")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Crossfade(
                    targetState = uiState.activeModule,
                    animationSpec = tween(200),
                    label = "moduleCrossfade"
                ) { module ->
                    when (module) {
                        StudioModule.CODEPAD_IDE -> {
                            CodePadStudioScreen(
                                uiState = uiState,
                                viewModel = studioViewModel,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        StudioModule.AUTO_WRITE_STUDIO -> {
                            AutoWriteStudioScreen(
                                uiState = uiState,
                                viewModel = studioViewModel,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}
