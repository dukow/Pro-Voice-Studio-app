package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.audio.AudioRecorder
import com.example.audio.VoicePreset
import com.example.audio.VoicePresets
import com.example.data.AudioTask
import com.example.data.Recording
import com.example.data.VoiceProject
import com.example.ui.PlaybackState
import com.example.ui.VoiceViewModel
import com.example.ui.theme.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppScreen()
            }
        }
    }
}

@Composable
fun MainAppScreen() {
    val context = LocalContext.current
    val viewModel: VoiceViewModel = viewModel()

    // Dynamic state trackers
    var hasRecordPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasRecordPermission = granted
        }
    )

    // Automatically trigger permission check if not granted
    LaunchedEffect(Unit) {
        if (!hasRecordPermission) {
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = CarbonDark
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (hasRecordPermission) {
                StudioWorkspace(viewModel = viewModel)
            } else {
                PermissionRequiredScreen(onRequestPermission = {
                    launcher.launch(Manifest.permission.RECORD_AUDIO)
                })
            }
        }
    }
}

@Composable
fun PermissionRequiredScreen(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(StudioSurfaceVariant, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MicOff,
                contentDescription = "Mic Disabled",
                tint = AmberNeon,
                modifier = Modifier.size(48.dp)
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Microphone Access Required",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Pro Voice Studio needs permission to capture audio from your device to record uncompressed vocal files and apply effects.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onRequestPermission,
            colors = ButtonDefaults.buttonColors(containerColor = AmberNeon),
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(48.dp)
                .testTag("grant_permission_button")
        ) {
            Icon(imageVector = Icons.Default.LockOpen, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Grant Microphone Access", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun StudioWorkspace(viewModel: VoiceViewModel) {
    var activeTab by remember { mutableStateOf(0) }
    
    // Active preloaded script task to record
    var preloadedTaskForRecording by remember { mutableStateOf<AudioTask?>(null) }

    // Navigation triggers
    val navigateToRecorder: (AudioTask?) -> Unit = { task ->
        preloadedTaskForRecording = task
        activeTab = 1 // Switch to Recorder Tab
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            StudioTopBar()

            // Main Content Body based on Active Tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (activeTab) {
                    0 -> ProjectsDashboardScreen(
                        viewModel = viewModel,
                        onRecordTask = navigateToRecorder
                    )
                    1 -> RecorderLabScreen(
                        viewModel = viewModel,
                        preloadedTask = preloadedTaskForRecording,
                        onClearTask = { preloadedTaskForRecording = null }
                    )
                    2 -> MasteringConsoleScreen(viewModel = viewModel)
                }
            }

            // Bottom Studio Tab Selector
            StudioNavigationBar(activeTab = activeTab, onTabSelected = { activeTab = it })
        }

        // Overlay Global FFmpeg progress indicator
        val isProcessing by viewModel.isProcessing.collectAsStateWithLifecycle()
        val progress by viewModel.processingProgress.collectAsStateWithLifecycle()

        if (isProcessing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = AmberNeon,
                        strokeWidth = 4.dp,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "FFmpeg Offline DSP Processing...",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Compiling audio files, filters, and bitstream channels safely offline.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        color = AmberNeon,
                        trackColor = StudioSurface,
                        modifier = Modifier
                            .width(200.dp)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                }
            }
        }
    }
}

@Composable
fun StudioTopBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(StudioSurface)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .border(width = 1.dp, color = StudioBorder, shape = RoundedCornerShape(0.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        Brush.linearGradient(listOf(AmberNeon, AmberGlow)),
                        RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = "Logo",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "PRO VOICE STUDIO",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = "OFFLINE DSP DECK • ACTIVE",
                    style = MaterialTheme.typography.labelSmall,
                    color = LevelGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 8.sp,
                    letterSpacing = 1.sp
                )
            }
        }

        // Live status indicator pill
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(StudioSurfaceVariant, RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(LevelGreen, CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "COLLAB LINKED",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun StudioNavigationBar(activeTab: Int, onTabSelected: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(StudioSurface)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val tabs = listOf(
            Triple("Collab Board", Icons.Default.Dashboard, 0),
            Triple("Record Lab", Icons.Default.Mic, 1),
            Triple("Masters Rack", Icons.Default.Audiotrack, 2)
        )

        tabs.forEach { (title, icon, index) ->
            val isSelected = activeTab == index
            val tint = if (isSelected) AmberNeon else TextMuted
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onTabSelected(index) }
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .width(80.dp)
                    .testTag("tab_$index")
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = tint,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = title,
                    color = tint,
                    fontSize = 10.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

// ==========================================
// TAB 0: PROJECTS & COLLABORATIVE BOARD
// ==========================================
@Composable
fun ProjectsDashboardScreen(
    viewModel: VoiceViewModel,
    onRecordTask: (AudioTask) -> Unit
) {
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val selectedProj by viewModel.selectedProject.collectAsStateWithLifecycle()

    var showCreateDialog by remember { mutableStateOf(false) }

    AnimatedContent(
        targetState = selectedProj,
        transitionSpec = {
            slideInHorizontally { width -> width } togetherWith slideOutHorizontally { width -> -width }
        },
        label = "ProjectPanelTransition"
    ) { activeProject ->
        if (activeProject == null) {
            // General projects list view
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Vocal Sessions Board",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Organize, assign, and record script lines directly.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Button(
                        onClick = { showCreateDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = StudioSurfaceVariant),
                        border = BorderStroke(1.dp, StudioBorder),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = AmberNeon, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New Project", color = TextPrimary, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (projects.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null, tint = TextMuted, modifier = Modifier.size(64.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("No Active Vocal Projects", color = TextSecondary, fontWeight = FontWeight.Bold)
                            Text("Create a project to start dubbing script lines.", color = TextMuted, fontSize = 12.sp)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(projects) { project ->
                            ProjectCard(
                                project = project,
                                onClick = { viewModel.selectProject(project) },
                                onDelete = { viewModel.deleteProject(project) }
                            )
                        }
                    }
                }
            }
        } else {
            // Single Project Task Dashboard
            ProjectTasksBoard(
                project = activeProject,
                viewModel = viewModel,
                onBack = { viewModel.selectProject(null) },
                onRecordTask = onRecordTask
            )
        }
    }

    // Modal to create project
    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = StudioSurface,
            title = { Text("Create Vocal Project", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Project Name") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AmberNeon,
                            unfocusedBorderColor = StudioBorder,
                            focusedLabelColor = AmberNeon,
                            unfocusedLabelColor = TextSecondary,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = desc,
                        onValueChange = { desc = it },
                        label = { Text("Description") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AmberNeon,
                            unfocusedBorderColor = StudioBorder,
                            focusedLabelColor = AmberNeon,
                            unfocusedLabelColor = TextSecondary,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            viewModel.createProject(name, desc)
                            showCreateDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AmberNeon)
                ) {
                    Text("Create", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
fun ProjectCard(
    project: VoiceProject,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = StudioSurface),
        border = BorderStroke(1.dp, StudioBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Folder, contentDescription = null, tint = AmberNeon, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = project.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 200.dp)
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = TextMuted, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = project.description,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(project.createdTimestamp)),
                    fontSize = 10.sp,
                    color = TextMuted
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Open Session", color = AmberGlow, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(imageVector = Icons.Default.ArrowForward, contentDescription = null, tint = AmberGlow, modifier = Modifier.size(12.dp))
                }
            }
        }
    }
}

@Composable
fun ProjectTasksBoard(
    project: VoiceProject,
    viewModel: VoiceViewModel,
    onBack: () -> Unit,
    onRecordTask: (AudioTask) -> Unit
) {
    val tasks by viewModel.projectTasks.collectAsStateWithLifecycle()
    var showAddTaskDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Project navigation header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Vocal Script Board",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
            IconButton(onClick = { showAddTaskDialog = true }) {
                Icon(imageVector = Icons.Default.PlaylistAdd, contentDescription = "Add Script Task", tint = AmberNeon)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Collaboration Stats Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(StudioSurface, RoundedCornerShape(12.dp))
                .border(1.dp, StudioBorder, RoundedCornerShape(12.dp))
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val todoCount = tasks.count { it.status == "TODO" }
            val doneCount = tasks.count { it.status == "RECORDED" || it.status == "PROCESSED" || it.status == "COMPLETED" }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("SCRIPTS", color = TextMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                Text("${tasks.size}", color = TextPrimary, fontWeight = FontWeight.Black, fontSize = 16.sp)
            }
            Box(modifier = Modifier.width(1.dp).height(24.dp).background(StudioBorder).align(Alignment.CenterVertically))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("PENDING", color = TextMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                Text("$todoCount", color = LevelRed, fontWeight = FontWeight.Black, fontSize = 16.sp)
            }
            Box(modifier = Modifier.width(1.dp).height(24.dp).background(StudioBorder).align(Alignment.CenterVertically))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("COMPLETED", color = TextMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                Text("$doneCount", color = LevelGreen, fontWeight = FontWeight.Black, fontSize = 16.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (tasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = Icons.Default.PlaylistPlay, contentDescription = null, tint = TextMuted, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No Script Lines Added", color = TextSecondary, fontWeight = FontWeight.Bold)
                    Text("Click the top right icon to add a characters dialogue.", color = TextMuted, fontSize = 11.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(tasks) { task ->
                    TaskLineItem(
                        task = task,
                        onRecord = { onRecordTask(task) },
                        onSimulateSync = { viewModel.simulateCoActorUpdate(task) },
                        onDelete = { viewModel.deleteTask(task) }
                    )
                }
            }
        }
    }

    if (showAddTaskDialog) {
        var characterName by remember { mutableStateOf("") }
        var scriptText by remember { mutableStateOf("") }
        var targetPreset by remember { mutableStateOf(VoicePresets.presets.first()) }
        var expandedPresets by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAddTaskDialog = false },
            containerColor = StudioSurface,
            title = { Text("Add Script Dialogue Line", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    OutlinedTextField(
                        value = characterName,
                        onValueChange = { characterName = it },
                        label = { Text("Character Name") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AmberNeon,
                            unfocusedBorderColor = StudioBorder,
                            focusedLabelColor = AmberNeon,
                            unfocusedLabelColor = TextSecondary,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = scriptText,
                        onValueChange = { scriptText = it },
                        label = { Text("Script Text / Dialogue") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AmberNeon,
                            unfocusedBorderColor = StudioBorder,
                            focusedLabelColor = AmberNeon,
                            unfocusedLabelColor = TextSecondary,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { expandedPresets = true },
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, StudioBorder),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Suggested Effect: ${targetPreset.name}")
                                Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        }
                        DropdownMenu(
                            expanded = expandedPresets,
                            onDismissRequest = { expandedPresets = false },
                            modifier = Modifier
                                .background(StudioSurfaceVariant)
                                .heightIn(max = 200.dp)
                        ) {
                            VoicePresets.presets.forEach { preset ->
                                DropdownMenuItem(
                                    text = { Text(preset.name, color = TextPrimary) },
                                    onClick = {
                                        targetPreset = preset
                                        expandedPresets = false
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (characterName.isNotBlank() && scriptText.isNotBlank()) {
                            viewModel.createTask(
                                projectId = project.id,
                                characterName = characterName,
                                scriptText = scriptText,
                                targetPresetId = targetPreset.id
                            )
                            showAddTaskDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AmberNeon)
                ) {
                    Text("Add Script", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTaskDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
fun TaskLineItem(
    task: AudioTask,
    onRecord: () -> Unit,
    onSimulateSync: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = StudioSurface),
        border = BorderStroke(1.dp, StudioBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(StudioSurfaceVariant, CircleShape)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = task.characterName.uppercase(),
                            color = AmberGlow,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    val presetName = VoicePresets.presets.find { it.id == task.targetPresetId }?.name ?: "Clean"
                    Text(
                        text = "FX: $presetName",
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val statusColor = when (task.status) {
                        "TODO" -> LevelRed
                        "RECORDED" -> LevelYellow
                        else -> LevelGreen
                    }
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(statusColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = task.status,
                        fontSize = 9.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = onDelete, modifier = Modifier.size(16.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Delete", tint = TextMuted, modifier = Modifier.size(14.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "\"${task.scriptText}\"",
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Assignee: ${task.assignedTo}",
                    fontSize = 11.sp,
                    color = TextMuted,
                    fontWeight = FontWeight.Medium
                )

                if (task.assignedTo == "You") {
                    Button(
                        onClick = onRecord,
                        colors = ButtonDefaults.buttonColors(containerColor = if (task.status == "TODO") AmberNeon else StudioSurfaceVariant),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(
                            imageVector = if (task.status == "TODO") Icons.Default.Mic else Icons.Default.Replay,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (task.status == "TODO") "Record Line" else "Re-record",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    // For co-actors, allow simulated sync
                    OutlinedButton(
                        onClick = onSimulateSync,
                        border = BorderStroke(1.dp, StudioBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(28.dp),
                        enabled = task.status == "TODO"
                    ) {
                        Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null, tint = AmberGlow, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Simulate Collab Sync", fontSize = 10.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ==========================================
// TAB 1: RECORDING TERMINAL LAB
// ==========================================
@Composable
fun RecorderLabScreen(
    viewModel: VoiceViewModel,
    preloadedTask: AudioTask?,
    onClearTask: () -> Unit
) {
    val status by viewModel.recorderStatus.collectAsStateWithLifecycle()
    val amplitude by viewModel.liveAmplitude.collectAsStateWithLifecycle()
    val elapsedMs by viewModel.liveDurationMs.collectAsStateWithLifecycle()

    val sampleRate by viewModel.sampleRate.collectAsStateWithLifecycle()
    val isStereo by viewModel.isStereo.collectAsStateWithLifecycle()
    val skipSilence by viewModel.skipSilence.collectAsStateWithLifecycle()

    var showConfigDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Preloaded Script Box
        if (preloadedTask != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, AmberNeon.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = StudioSurfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Description, contentDescription = null, tint = AmberNeon, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Recording Character Script Line",
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(onClick = onClearTask, modifier = Modifier.size(20.dp)) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Clear", tint = TextMuted, modifier = Modifier.size(12.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Character: ${preloadedTask.characterName.uppercase()}",
                        color = AmberGlow,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "\"${preloadedTask.scriptText}\"",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        modifier = Modifier.padding(vertical = 4.dp),
                        lineHeight = 16.sp
                    )
                }
            }
        } else {
            // General record mode header
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = StudioSurface),
                border = BorderStroke(1.dp, StudioBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Mic, contentDescription = null, tint = AmberNeon)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Standalone Voice Capturer", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("Saves high-fidelity raw WAV/PCM audio directly to rack masters.", color = TextSecondary, fontSize = 10.sp)
                    }
                }
            }
        }

        // Live meter area with Canvas drawing
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            GlowingTerminalMeter(
                status = status,
                amplitude = amplitude
            )
        }

        // Digital counter and active config row
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val totalSec = elapsedMs / 1000
            val min = totalSec / 60
            val sec = totalSec % 60
            val ms = (elapsedMs % 1000) / 100
            val timerText = String.format("%02d:%02d.%d", min, sec, ms)

            Text(
                text = timerText,
                fontSize = 48.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = if (status == AudioRecorder.Status.RECORDING) AmberNeon else TextPrimary,
                letterSpacing = 2.sp
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(StudioSurface, RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .clickable { showConfigDialog = true }
            ) {
                Icon(imageVector = Icons.Default.SettingsInputAntenna, contentDescription = null, tint = AmberGlow, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "WAV • ${sampleRate / 1000f}kHz • 16-bit • ${if (isStereo) "Stereo" else "Mono"}",
                    color = TextPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Config", tint = TextMuted, modifier = Modifier.size(10.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Silence skip & external mic toggle panel
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(StudioSurface, RoundedCornerShape(12.dp))
                .border(1.dp, StudioBorder, RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.VolumeMute, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Skip Silence Toggle", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Text("Ignores audio frames below recording noise floor", color = TextSecondary, fontSize = 10.sp)
            }
            Switch(
                checked = skipSilence,
                onCheckedChange = { viewModel.toggleSkipSilence(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = AmberNeon,
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = StudioSurfaceVariant
                )
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Recorder Control Center Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (status != AudioRecorder.Status.IDLE) {
                // Pause / Resume button
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(StudioSurfaceVariant, CircleShape)
                        .clickable {
                            if (status == AudioRecorder.Status.PAUSED) viewModel.resumeRecording() else viewModel.pauseRecording()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (status == AudioRecorder.Status.PAUSED) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = "Pause Toggle",
                        tint = TextPrimary
                    )
                }

                Spacer(modifier = Modifier.width(32.dp))
            }

            // Central Record Toggle Circle
            val isRecordingActive = status == AudioRecorder.Status.RECORDING || status == AudioRecorder.Status.PAUSED
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(if (isRecordingActive) Color.White else AmberNeon, CircleShape)
                    .border(4.dp, StudioSurface, CircleShape)
                    .clickable {
                        if (isRecordingActive) {
                            viewModel.stopRecording(preloadedTask)
                            if (preloadedTask != null) {
                                onClearTask()
                            }
                        } else {
                            viewModel.startRecording(preloadedTask)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isRecordingActive) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = "Record Control",
                    tint = if (isRecordingActive) CarbonDark else Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            if (status != AudioRecorder.Status.IDLE) {
                Spacer(modifier = Modifier.width(32.dp))

                // Dummy slot for balanced spacing
                Box(modifier = Modifier.size(48.dp))
            }
        }
    }

    // Encoder configurations modal
    if (showConfigDialog) {
        var selectedRate by remember { mutableStateOf(sampleRate) }
        var selectedStereo by remember { mutableStateOf(isStereo) }

        AlertDialog(
            onDismissRequest = { showConfigDialog = false },
            containerColor = StudioSurface,
            title = { Text("Recording Format Config", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column {
                        Text("Sample Rate", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(44100, 48000, 96000).forEach { rate ->
                                val active = selectedRate == rate
                                OutlinedButton(
                                    onClick = { selectedRate = rate },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (active) AmberNeon else StudioSurfaceVariant
                                    ),
                                    border = BorderStroke(1.dp, if (active) AmberNeon else StudioBorder),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(4.dp)
                                ) {
                                    Text("${rate / 1000f} kHz", color = if (active) Color.White else TextPrimary, fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    Column {
                        Text("Channels", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(false, true).forEach { stereo ->
                                val active = selectedStereo == stereo
                                OutlinedButton(
                                    onClick = { selectedStereo = stereo },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (active) AmberNeon else StudioSurfaceVariant
                                    ),
                                    border = BorderStroke(1.dp, if (active) AmberNeon else StudioBorder),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(if (stereo) "Stereo" else "Mono", color = if (active) Color.White else TextPrimary, fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    // Auxiliary External Mic tip
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(StudioSurfaceVariant, RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = AmberGlow, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "External microphones (USB-C & Bluetooth accessories) will automatically override internal capturing device.",
                            color = TextSecondary,
                            fontSize = 9.sp,
                            lineHeight = 11.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setRecordingConfig(selectedRate, selectedStereo)
                        showConfigDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AmberNeon)
                ) {
                    Text("Save Config", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfigDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
fun GlowingTerminalMeter(
    status: AudioRecorder.Status,
    amplitude: Float
) {
    val infiniteTransition = rememberInfiniteTransition(label = "RadarOuterGlow")
    
    // Scale pulsation based on actual microphone signal amplitude
    val livePulseScale by animateFloatAsState(
        targetValue = 1f + (amplitude * 1.5f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "LivePulse"
    )

    // Secondary automatic idle breather animation
    val breather by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "IdleBreather"
    )

    val scale = if (status == AudioRecorder.Status.RECORDING) livePulseScale else breather
    val color = if (status == AudioRecorder.Status.RECORDING) AmberNeon else if (status == AudioRecorder.Status.PAUSED) LevelYellow else TextMuted

    Canvas(modifier = Modifier.size(180.dp)) {
        val radius = size.minDimension / 2
        val center = Offset(size.width / 2, size.height / 2)

        // 1. Draw outer neon radiating ripples
        drawCircle(
            color = color.copy(alpha = 0.08f * (amplitude * 2f).coerceAtMost(1f)),
            radius = radius * scale,
            center = center
        )

        drawCircle(
            color = color.copy(alpha = 0.15f),
            radius = radius * 0.8f * scale,
            center = center,
            style = Stroke(width = 2.dp, pathEffect = null)
        )

        // 2. Draw central main tube sphere
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = 0.8f), color.copy(alpha = 0.1f)),
                center = center,
                radius = radius * 0.5f
            ),
            radius = radius * 0.52f,
            center = center
        )

        // 3. Draw live audio waveform signal lines circling around the center
        if (status == AudioRecorder.Status.RECORDING) {
            val pointCount = 120
            val lineStroke = 3f
            for (i in 0 until pointCount) {
                val angle = (i.toFloat() / pointCount) * 2 * Math.PI
                // Modulate soundwave depth dynamically
                val waveOffset = sin(angle * 8 + System.currentTimeMillis() / 80) * (amplitude * radius * 0.35f)
                val baseRadius = radius * 0.55f + waveOffset

                val x = center.x + (baseRadius * Math.cos(angle)).toFloat()
                val y = center.y + (baseRadius * Math.sin(angle)).toFloat()

                drawCircle(
                    color = AmberGlow,
                    radius = lineStroke,
                    center = Offset(x, y)
                )
            }
        }
    }
}

// ==========================================
// TAB 2: MASTER CONSOLE & VOICE CHANGER RACK
// ==========================================
@Composable
fun MasteringConsoleScreen(viewModel: VoiceViewModel) {
    val recordings by viewModel.recordings.collectAsStateWithLifecycle()
    val activeRec by viewModel.activeRecording.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (activeRec == null) {
            // Audio list selection view
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = "Masters Studio Library",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Select a raw recording track to trim, change voices, or export.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (recordings.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(imageVector = Icons.Default.LibraryMusic, contentDescription = null, tint = TextMuted, modifier = Modifier.size(64.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("No Mastering Tracks Found", color = TextSecondary, fontWeight = FontWeight.Bold)
                            Text("Go to 'Record Lab' to record your first studio master.", color = TextMuted, fontSize = 12.sp)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(recordings) { rec ->
                            RecordingLibraryCard(
                                recording = rec,
                                onSelect = { viewModel.setActiveRecording(rec) },
                                onDelete = { viewModel.deleteRecording(rec) }
                            )
                        }
                    }
                }
            }
        } else {
            // Open Mastering Console
            RecordingMasterDeck(
                recording = activeRec,
                viewModel = viewModel,
                onBack = { viewModel.setActiveRecording(null) }
            )
        }
    }
}

@Composable
fun RecordingLibraryCard(
    recording: Recording,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        colors = CardDefaults.cardColors(containerColor = StudioSurface),
        border = BorderStroke(1.dp, StudioBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(StudioSurfaceVariant, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (recording.appliedPreset != null) Icons.Default.GraphicEq else Icons.Default.Mic,
                        contentDescription = null,
                        tint = if (recording.appliedPreset != null) AmberGlow else LevelGreen
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = recording.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${recording.sampleRate / 1000f}kHz • ${if (recording.channels == 2) "Stereo" else "Mono"}",
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                        if (recording.appliedPreset != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .background(AmberNeon.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = recording.appliedPreset.uppercase(),
                                    color = AmberNeon,
                                    fontSize = 7.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = recording.formattedDuration,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = String.format("%.1f MB", recording.fileSize / (1024f * 1024f)),
                        fontSize = 9.sp,
                        color = TextMuted
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onDelete) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = TextMuted, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun RecordingMasterDeck(
    recording: Recording,
    viewModel: VoiceViewModel,
    onBack: () -> Unit
) {
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val isPlaying = playbackState.isPlaying && playbackState.currentRecordingId == recording.id

    var activeSettingTab by remember { mutableStateOf(0) } // 0: Voice Changer, 1: Enhancer EQ, 2: Crop/Trim, 3: Export

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Back Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Text(
                text = "Back to Library",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Large Player Console Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = StudioSurface),
            border = BorderStroke(1.dp, StudioBorder)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = recording.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Absolute Path: .../${File(recording.filePath).name}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(if (recording.isEdited) AmberNeon.copy(alpha = 0.15f) else StudioSurfaceVariant, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (recording.isEdited) "DSP PROCESSED" else "RAW MASTER",
                            color = if (recording.isEdited) AmberNeon else TextSecondary,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Waveform playback illustration using Canvas
                PlaybackVisualizationCanvas(isPlaying = isPlaying)

                Spacer(modifier = Modifier.height(16.dp))

                // Playback slider track
                val position = if (playbackState.currentRecordingId == recording.id) playbackState.currentPositionMs else 0L
                val duration = recording.durationMs
                val progress = if (duration > 0) position.toFloat() / duration else 0f

                Slider(
                    value = progress,
                    onValueChange = { percent ->
                        val targetPos = (percent * duration).toLong()
                        viewModel.seekPlayback(targetPos)
                    },
                    colors = SliderDefaults.colors(
                        activeTrackColor = AmberNeon,
                        inactiveTrackColor = StudioBorder,
                        thumbColor = AmberNeon
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Position counters
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = String.format("%02d:%02d.%d", (position / 1000) / 60, (position / 1000) % 60, (position % 1000) / 100),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextSecondary
                    )
                    Text(
                        text = recording.formattedDuration,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Main deck button play trigger
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(AmberNeon, CircleShape)
                            .clickable {
                                if (isPlaying) viewModel.pausePlayback() else viewModel.playRecording(recording)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Rack selection buttons
        ScrollableTabRow(
            selectedTabIndex = activeSettingTab,
            containerColor = Color.Transparent,
            contentColor = AmberNeon,
            edgePadding = 0.dp,
            divider = {}
        ) {
            val sections = listOf("Voice Changer", "Studio FX Rack", "Trim Tool", "Export Gate")
            sections.forEachIndexed { idx, title ->
                Tab(
                    selected = activeSettingTab == idx,
                    onClick = { activeSettingTab = idx },
                    text = { Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Active Mastering Deck Setting Panel
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(StudioSurface, RoundedCornerShape(12.dp))
                .border(1.dp, StudioBorder, RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            when (activeSettingTab) {
                0 -> VoiceChangerRack(recording = recording, onApplyPreset = { preset ->
                    viewModel.applyVoicePreset(recording, preset)
                })
                1 -> StudioFXRack(recording = recording, onApplyEnhancements = { nr, comp, b, m, t ->
                    viewModel.applyStudioEnhancements(recording, nr, comp, b, m, t)
                })
                2 -> AudioTrimTool(recording = recording, onTrim = { start, end ->
                    viewModel.trimRecording(recording, start, end)
                })
                3 -> AudioExportGate(recording = recording, viewModel = viewModel)
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun PlaybackVisualizationCanvas(isPlaying: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "WaveAnimation")
    val phaseShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2 * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PhaseShift"
    )

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(StudioSurfaceVariant, RoundedCornerShape(6.dp))
    ) {
        val width = size.width
        val height = size.height
        val midY = height / 2

        if (isPlaying) {
            // Draw dual sinusoidal signal waves representing playback activity
            val points = 80
            val strokeWidth = 3f

            for (i in 0 until points) {
                val x = (i.toFloat() / points) * width
                
                // Sin wave 1
                val angle1 = (i.toFloat() / points) * 4 * Math.PI.toFloat() + phaseShift
                val y1 = midY + (sin(angle1) * (height * 0.35f)).toFloat()

                // Sin wave 2
                val angle2 = (i.toFloat() / points) * 6 * Math.PI.toFloat() - phaseShift
                val y2 = midY + (sin(angle2) * (height * 0.2f)).toFloat()

                drawCircle(
                    color = AmberNeon,
                    radius = strokeWidth,
                    center = Offset(x, y1)
                )

                drawCircle(
                    color = AmberGlow.copy(alpha = 0.5f),
                    radius = strokeWidth,
                    center = Offset(x, y2)
                )
            }
        } else {
            // Draw static waveform timeline ticks
            val ticks = 40
            val gap = width / ticks
            for (i in 0 until ticks) {
                val x = i * gap + gap / 2
                val tickHeight = (i % 5 + 2) * 4f
                drawLine(
                    color = TextMuted,
                    start = Offset(x, midY - tickHeight),
                    end = Offset(x, midY + tickHeight),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

// === Mastering Sub-Tab 0: Voice Changer ===
@Composable
fun VoiceChangerRack(
    recording: Recording,
    onApplyPreset: (VoicePreset) -> Unit
) {
    var selectedCategory by remember { mutableStateOf(VoicePresets.CAT_GENDER_AGE) }
    val categories = listOf(
        VoicePresets.CAT_GENDER_AGE,
        VoicePresets.CAT_SCIFI_SPACE,
        VoicePresets.CAT_ENVIRONMENT,
        VoicePresets.CAT_DEVICES,
        VoicePresets.CAT_SPEED_PITCH,
        VoicePresets.CAT_COMICAL
    )

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("DSP Voice Changer presets", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Offline digital processing presets to apply to vocals.", color = TextSecondary, fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Horizontal Category Row Scroll
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.forEach { cat ->
                val active = selectedCategory == cat
                Box(
                    modifier = Modifier
                        .background(
                            if (active) AmberNeon else StudioSurfaceVariant,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { selectedCategory = cat }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(cat, color = if (active) Color.White else TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Presets Grid
        val filteredPresets = VoicePresets.presets.filter { it.category == selectedCategory }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.height(280.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filteredPresets) { preset ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onApplyPreset(preset) },
                    colors = CardDefaults.cardColors(containerColor = StudioSurfaceVariant),
                    border = BorderStroke(1.dp, StudioBorder)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = null,
                                tint = AmberNeon,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(preset.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = preset.description,
                            color = TextSecondary,
                            fontSize = 9.sp,
                            lineHeight = 11.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

// === Mastering Sub-Tab 1: Enhancements & 3-Band EQ ===
@Composable
fun StudioFXRack(
    recording: Recording,
    onApplyEnhancements: (Boolean, Boolean, Float, Float, Float) -> Unit
) {
    var noiseReduction by remember { mutableStateOf(false) }
    var compressorLimiter by remember { mutableStateOf(false) }
    var bassSlider by remember { mutableStateOf(0f) }   // -10dB to +10dB
    var midSlider by remember { mutableStateOf(0f) }
    var trebleSlider by remember { mutableStateOf(0f) }

    Column {
        Text("Studio FX & Mastering Rack", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text("Apply low-latency FFT denoisers, dynamic companders, and equalizers.", color = TextSecondary, fontSize = 11.sp)

        Spacer(modifier = Modifier.height(16.dp))

        // Noise Reduction Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(StudioSurfaceVariant, RoundedCornerShape(8.dp))
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Hearing, contentDescription = null, tint = AmberNeon, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("FFT Audio Noise Gate Denoiser", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("Reduces background microphone clicks and hums.", color = TextSecondary, fontSize = 9.sp)
                }
            }
            Switch(
                checked = noiseReduction,
                onCheckedChange = { noiseReduction = it },
                colors = SwitchDefaults.colors(checkedTrackColor = AmberNeon)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Compressor Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(StudioSurfaceVariant, RoundedCornerShape(8.dp))
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Compress, contentDescription = null, tint = AmberNeon, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Dynamic Compander Compressor & Limiter", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("Warm levels, boosts quiet vocals, prevents peak clipping.", color = TextSecondary, fontSize = 9.sp)
                }
            }
            Switch(
                checked = compressorLimiter,
                onCheckedChange = { compressorLimiter = it },
                colors = SwitchDefaults.colors(checkedTrackColor = AmberNeon)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3-Band EQ Sliders
        Text("3-Band Analog Equalizer", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(12.dp))

        // BASS
        EQSliderItem(title = "BASS (120 Hz)", value = bassSlider, onValueChange = { bassSlider = it })
        // MID
        EQSliderItem(title = "MIDRANGE (1.0 kHz)", value = midSlider, onValueChange = { midSlider = it })
        // TREBLE
        EQSliderItem(title = "TREBLE (8.0 kHz)", value = trebleSlider, onValueChange = { trebleSlider = it })

        Spacer(modifier = Modifier.height(16.dp))

        // Apply FX button
        Button(
            onClick = {
                onApplyEnhancements(
                    noiseReduction,
                    compressorLimiter,
                    bassSlider,
                    midSlider,
                    trebleSlider
                )
            },
            colors = ButtonDefaults.buttonColors(containerColor = AmberNeon),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(imageVector = Icons.Default.Tune, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Compile & Render FX", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun EQSliderItem(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, color = TextSecondary, fontSize = 11.sp)
            Text(String.format("%+.1f dB", value), color = AmberGlow, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = -10f..10f,
            colors = SliderDefaults.colors(
                activeTrackColor = AmberNeon,
                inactiveTrackColor = StudioBorder,
                thumbColor = AmberNeon
            )
        )
    }
}

// === Mastering Sub-Tab 2: Trimming/Editing ===
@Composable
fun AudioTrimTool(
    recording: Recording,
    onTrim: (Long, Long) -> Unit
) {
    val duration = recording.durationMs
    var startPercentage by remember { mutableStateOf(0f) }
    var endPercentage by remember { mutableStateOf(1f) }

    val startMs = (startPercentage * duration).toLong()
    val endMs = (endPercentage * duration).toLong()

    Column {
        Text("Waveform Trimming Tool", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text("Slide endpoints to frame start/end times precisely and crop the master.", color = TextSecondary, fontSize = 11.sp)

        Spacer(modifier = Modifier.height(24.dp))

        // Position sliders
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("START CUT-POINT", color = TextSecondary, fontSize = 11.sp)
                    Text(
                        text = String.format("%02d:%02d.%d", (startMs / 1000) / 60, (startMs / 1000) % 60, (startMs % 1000) / 100),
                        color = AmberNeon, fontSize = 11.sp, fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = startPercentage,
                    onValueChange = {
                        if (it < endPercentage - 0.05f) startPercentage = it
                    },
                    colors = SliderDefaults.colors(activeTrackColor = AmberNeon, thumbColor = AmberNeon)
                )
            }

            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("END CUT-POINT", color = TextSecondary, fontSize = 11.sp)
                    Text(
                        text = String.format("%02d:%02d.%d", (endMs / 1000) / 60, (endMs / 1000) % 60, (endMs % 1000) / 100),
                        color = AmberNeon, fontSize = 11.sp, fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = endPercentage,
                    onValueChange = {
                        if (it > startPercentage + 0.05f) endPercentage = it
                    },
                    colors = SliderDefaults.colors(activeTrackColor = AmberNeon, thumbColor = AmberNeon)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { onTrim(startMs, endMs) },
            colors = ButtonDefaults.buttonColors(containerColor = AmberNeon),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(imageVector = Icons.Default.ContentCut, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Crop & Save Trimmed File", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

// === Mastering Sub-Tab 3: Export Conversions ===
@Composable
fun AudioExportGate(
    recording: Recording,
    viewModel: VoiceViewModel
) {
    val context = LocalContext.current
    var selectedFormat by remember { mutableStateOf("MP3") }
    var selectedQuality by remember { mutableStateOf("High") }

    Column {
        Text("Export & Sharing Terminal", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text("Encode high-fidelity files into standard portable codecs offline.", color = TextSecondary, fontSize = 11.sp)

        Spacer(modifier = Modifier.height(16.dp))

        // Format selection row
        Text("PORTABLE AUDIO CODEC", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("WAV", "MP3", "FLAC", "AAC").forEach { fmt ->
                val active = selectedFormat == fmt
                OutlinedButton(
                    onClick = { selectedFormat = fmt },
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = if (active) AmberNeon else StudioSurfaceVariant),
                    border = BorderStroke(1.dp, if (active) AmberNeon else StudioBorder),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(2.dp)
                ) {
                    Text(fmt, color = if (active) Color.White else TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Quality selection row
        Text("ENCODER QUALITY ACCURACY", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Low", "Medium", "High").forEach { qty ->
                val active = selectedQuality == qty
                OutlinedButton(
                    onClick = { selectedQuality = qty },
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = if (active) AmberNeon else StudioSurfaceVariant),
                    border = BorderStroke(1.dp, if (active) AmberNeon else StudioBorder),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(qty, color = if (active) Color.White else TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                viewModel.exportRecording(recording, selectedFormat, selectedQuality) { file ->
                    shareAudioFile(context, file)
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = AmberNeon),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(imageVector = Icons.Default.Share, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Convert & Share Audio", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

fun shareAudioFile(context: Context, file: File) {
    try {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Finished Voice Track"))
    } catch (e: Exception) {
        Toast.makeText(context, "Sharing failed: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
