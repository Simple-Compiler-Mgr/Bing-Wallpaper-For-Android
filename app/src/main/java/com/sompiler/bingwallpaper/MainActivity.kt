package com.sompiler.bingwallpaper

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.Coil
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sompiler.bingwallpaper.ui.theme.BingWallpaperTheme
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import java.io.File

// Bing API 接口
interface BingWallpaperApi {
    @GET("HPImageArchive.aspx?format=js&idx=0&n=30&mkt=zh-CN")
    suspend fun getWallpapers(): BingResponse
    
    @GET("HPImageArchive.aspx?format=js&idx=30&n=30&mkt=zh-CN")
    suspend fun getMoreWallpapers(): BingResponse
}

// 数据类
data class BingResponse(val images: List<BingImage>)
data class BingImage(val url: String, val copyright: String)

// ViewModel
class WallpaperViewModel : androidx.lifecycle.ViewModel() {
    private val api = Retrofit.Builder()
        .baseUrl("https://www.bing.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(BingWallpaperApi::class.java)

    var wallpapers by mutableStateOf<List<BingImage>>(emptyList())
        private set
    var selectedImage by mutableStateOf<BingImage?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var showPreview by mutableStateOf(false)
        private set
    var hasMore by mutableStateOf(true)
        private set
    var isLoadingMore by mutableStateOf(false)
        private set
    var isListMode by mutableStateOf(false)
        private set
    var showSettings by mutableStateOf(false)
        private set

    suspend fun fetchWallpapers() {
        isLoading = true
        try {
            val response = api.getWallpapers()
            wallpapers = response.images.map { image ->
                image.copy(url = "https://www.bing.com${image.url}")
            }
        } catch (e: Exception) {
            // 处理错误
        } finally {
            isLoading = false
        }
    }

    suspend fun loadMore() {
        if (isLoadingMore || !hasMore) return
        isLoadingMore = true
        try {
            val response = api.getMoreWallpapers()
            if (response.images.isEmpty()) {
                hasMore = false
            } else {
                wallpapers = wallpapers + response.images.map { image ->
                    image.copy(url = "https://www.bing.com${image.url}")
                }
            }
        } catch (e: Exception) {
            // 处理错误
        } finally {
            isLoadingMore = false
        }
    }

    fun showImagePreview(image: BingImage) {
        selectedImage = image
        showPreview = true
    }

    fun hidePreview() {
        showPreview = false
        selectedImage = null
    }

    suspend fun setWallpaper(context: Context) {
        isLoading = true
        try {
            selectedImage?.let { image ->
                val bitmap = Coil.imageLoader(context)
                    .execute(ImageRequest.Builder(context).data(image.url).build())
                    .drawable?.toBitmap()
                
                bitmap?.let {
                    // 保存图片到临时文件
                    val tmpFile = File(context.cacheDir, "wallpaper.jpg")
                    tmpFile.outputStream().use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                    }
                    
                    // 创建 content:// URI
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        tmpFile
                    )
                    
                    val intent = Intent(Intent.ACTION_SET_WALLPAPER)
                    val chooser = Intent.createChooser(intent, "设置壁纸")
                    
                    context.startActivity(chooser)
                }
            }
        } catch (e: Exception) {
            // 处理错误
        } finally {
            isLoading = false
        }
    }

    fun toggleViewMode() {
        isListMode = !isListMode
    }

    fun toggleSettings() {
        showSettings = !showSettings
    }

    fun hideSettings() {
        showSettings = false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BingWallpaperTheme {
                WallpaperScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperScreen(viewModel: WallpaperViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        viewModel.fetchWallpapers()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Bing Sompiler",
                        style = MaterialTheme.typography.headlineMedium
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleViewMode() }) {
                        Icon(
                            if (viewModel.isListMode) 
                                Icons.Default.Close
                            else 
                                Icons.Default.Menu,
                            contentDescription = if (viewModel.isListMode) "切换到网格视图" else "切换到列表视图"
                        )
                    }
                    IconButton(onClick = { viewModel.toggleSettings() }) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "设置"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
            ) {
                if (viewModel.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }

                LazyVerticalGrid(
                    columns = if (viewModel.isListMode) GridCells.Fixed(1) else GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // 今天的壁纸（大图）
                    viewModel.wallpapers.firstOrNull()?.let { todayImage ->
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            WallpaperThumbnail(
                                image = todayImage,
                                onClick = { viewModel.showImagePreview(todayImage) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(320.dp),
                                isListMode = false // 大图始终使用网格模式
                            )
                        }
                    }

                    // 历史壁纸
                    items(viewModel.wallpapers.drop(1)) { image ->
                        WallpaperThumbnail(
                            image = image,
                            onClick = { viewModel.showImagePreview(image) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (viewModel.isListMode) 128.dp else 180.dp),
                            isListMode = viewModel.isListMode
                        )
                    }
                    
                    // 加载更多
                    item(span = { GridItemSpan(2) }) {
                        if (viewModel.hasMore) {
                            LaunchedEffect(Unit) {
                                viewModel.loadMore()
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            }

            // 预览弹窗
            if (viewModel.showPreview) {
                WallpaperPreview(
                    image = viewModel.selectedImage!!,
                    onDismiss = { viewModel.hidePreview() },
                    onSetWallpaper = {
                        scope.launch {
                            viewModel.setWallpaper(context)
                        }
                    }
                )
            }

            // 设置对话框
            if (viewModel.showSettings) {
                var isDarkTheme by remember { mutableStateOf(false) }  // 这里可以连接到实际的主题系统
                SettingsDialog(
                    onDismiss = { viewModel.hideSettings() },
                    onToggleTheme = { isDarkTheme = !isDarkTheme },
                    isDarkTheme = isDarkTheme
                )
            }
        }
    }
}

@Composable
fun WallpaperThumbnail(
    image: BingImage,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isListMode: Boolean = false
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        if (isListMode) {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(2f)
                ) {
                    AsyncImage(
                        model = image.url,
                        contentDescription = image.copyright,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                
                Column(
                    modifier = Modifier
                        .weight(3f)
                        .fillMaxHeight()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = image.copyright,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        } else {
            // 网格视图布局保持不变
            Box {
                AsyncImage(
                    model = image.url,
                    contentDescription = image.copyright,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.4f),
                                    Color.Black.copy(alpha = 0.75f),
                                    Color.Black.copy(alpha = 0.9f)
                                ),
                                startY = 0f,
                                endY = 200f
                            )
                        )
                        .padding(16.dp)
                ) {
                    Text(
                        text = image.copyright,
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .padding(bottom = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun WallpaperPreview(
    image: BingImage,
    onDismiss: () -> Unit,
    onSetWallpaper: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
        ) {
            // 关闭按钮
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "关闭",
                    tint = Color.White
                )
            }

            // 图片
            AsyncImage(
                model = image.url,
                contentDescription = image.copyright,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )

            // 改进的底部信息显示
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.8f)
                            ),
                            startY = 0f,
                            endY = 200f
                        )
                    )
                    .padding(16.dp)
            ) {
                Text(
                    text = image.copyright,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Button(
                    onClick = onSetWallpaper,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                    )
                ) {
                    Text("设置为壁纸")
                }
            }
        }
    }
}

@Composable
fun SettingsDialog(
    onDismiss: () -> Unit,
    onToggleTheme: () -> Unit,
    isDarkTheme: Boolean
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "设置",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // 深色模式开关
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onToggleTheme)
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "深色模式",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Switch(
                        checked = isDarkTheme,
                        onCheckedChange = { onToggleTheme() }
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 分割线
                Divider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                )
                
                // 关于信息
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "Bing Wallpaper By Sompiler",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "作者: B站Simple Compiler",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}