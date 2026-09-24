@file:Suppress("LongMethod", "TooManyFunctions", "MagicNumber")

package io.github.timkabot.performance.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.timkabot.performance.api.PerformanceOwner
import io.github.timkabot.performance.api.PerformanceSpace
import io.github.timkabot.performance.api.PerformanceStage
import io.github.timkabot.performance.api.PerformanceTrace
import io.github.timkabot.performance.tracing.DataReadyCondition
import io.github.timkabot.performance.compose.UiScreenTracer
import io.github.timkabot.performance.compose.UiWidgetTracer
import io.github.timkabot.performance.runtime.PerformanceMeasure

private val AppBackground = Color(0xFFF4F6F8)
private val Ink = Color(0xFF17202A)
private val Muted = Color(0xFF667085)
private val Border = Color(0xFFE1E6EB)
private val Accent = Color(0xFF0E7C66)
private val AccentSoft = Color(0xFFE4F4EF)
private val Warning = Color(0xFFB45F06)
private val WarningSoft = Color(0xFFFFF3DF)
private val PendingSoft = Color(0xFFF0F2F5)
private val LogBackground = Color(0xFF111827)
private val CardSurface = Color.White
private val ButtonText = Color.White
private val StepCompleted = Color(0xFFDFF3EC)
private val TrackLine = Color(0xFFD9E6E1)
private val SampleColorScheme = lightColorScheme(
    primary = Accent,
    onPrimary = ButtonText,
    primaryContainer = AccentSoft,
    onPrimaryContainer = Accent,
    surface = AppBackground,
    onSurface = Ink,
    outline = Border,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = SampleColorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = AppBackground,
                ) {
                    PerformanceSampleApp()
                }
            }
        }
    }
}

@Composable
private fun PerformanceSampleApp() {
    val navController = rememberNavController()
    val catalogFeature = remember { CatalogFeature() }
    val productFeature = remember { ProductFeature() }
    val checkoutFeature = remember { CheckoutFeature() }
    val recommendationsFeature = remember { RecommendationsFeature() }
    val logs by SampleLogStore.logs.collectAsState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val activeRoute = SampleRoute.fromRoute(backStackEntry?.destination?.route)

    Column(modifier = Modifier.fillMaxSize()) {
        SampleHeader(
            activeRoute = activeRoute,
            onClearLogs = SampleLogStore::clear,
            onRouteSelected = { route ->
                navController.openRoute(route, productFeature, checkoutFeature)
            },
        )
        SampleNavHost(
            navController = navController,
            catalogFeature = catalogFeature,
            productFeature = productFeature,
            checkoutFeature = checkoutFeature,
            recommendationsFeature = recommendationsFeature,
            modifier = Modifier.weight(1f),
        )
        LogsPanel(
            logs = logs,
            modifier = Modifier
                .fillMaxWidth()
                .height(168.dp),
        )
    }
}

private fun NavHostController.openRoute(
    route: SampleRoute,
    productFeature: ProductFeature,
    checkoutFeature: CheckoutFeature,
) {
    when (route) {
        SampleRoute.Catalog -> navigate(SampleRoute.Catalog.route) {
            popUpTo(SampleRoute.Catalog.route) {
                inclusive = true
            }
        }
        SampleRoute.Product -> {
            productFeature.reset()
            navigate(SampleRoute.Product.route) {
                launchSingleTop = true
            }
        }
        SampleRoute.Checkout -> {
            checkoutFeature.reset()
            navigate(SampleRoute.Checkout.route) {
                launchSingleTop = true
            }
        }
    }
}

@Composable
private fun SampleHeader(
    activeRoute: SampleRoute,
    onClearLogs: () -> Unit,
    onRouteSelected: (SampleRoute) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Performance SDK",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Ink,
                )
                Text(
                    text = "Catalog -> Product -> Checkout",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
            }
            IconButton(onClick = onClearLogs) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Clear event log",
                    tint = Muted,
                )
            }
        }
        JourneyStepper(
            activeRoute = activeRoute,
            onRouteSelected = onRouteSelected,
        )
    }
}

@Composable
private fun JourneyStepper(
    activeRoute: SampleRoute,
    onRouteSelected: (SampleRoute) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SampleRoute.entries.forEach { route ->
            val selected = route == activeRoute
            val completed = route.ordinal < activeRoute.ordinal
            val borderColor = if (selected) Accent else Border
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        when {
                            selected -> AccentSoft
                            completed -> StepCompleted
                            else -> CardSurface
                        },
                    )
                    .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(10.dp))
                    .clickable { onRouteSelected(route) }
                    .padding(vertical = 10.dp, horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                StepDot(
                    text = if (completed) "✓" else route.number.toString(),
                    active = selected || completed,
                )
                Text(
                    text = route.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) Accent else Ink,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun StepDot(
    text: String,
    active: Boolean,
) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(if (active) Accent else PendingSoft),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) Color.White else Muted,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SampleNavHost(
    navController: NavHostController,
    catalogFeature: CatalogFeature,
    productFeature: ProductFeature,
    checkoutFeature: CheckoutFeature,
    recommendationsFeature: RecommendationsFeature,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = SampleRoute.Catalog.route,
        modifier = modifier,
    ) {
        composable(SampleRoute.Catalog.route) {
            UiScreenTracer(
                space = SampleScreen.Catalog,
                owner = SampleOwner.DemoApp,
                viewName = "CatalogScreen",
                dataReadyCondition = rememberDataReady(catalogFeature.dataReady),
            ) {
                CatalogScreen(
                    feature = catalogFeature,
                    recommendationsFeature = recommendationsFeature,
                    openProduct = {
                        productFeature.reset()
                        navController.navigate(SampleRoute.Product.route)
                    },
                )
            }
        }
        composable(SampleRoute.Product.route) {
            UiScreenTracer(
                space = SampleScreen.Product,
                owner = SampleOwner.DemoApp,
                viewName = "ProductScreen",
                dataReadyCondition = rememberDataReady(productFeature.dataReady),
            ) {
                ProductScreen(
                    feature = productFeature,
                    openCheckout = {
                        checkoutFeature.reset()
                        navController.navigate(SampleRoute.Checkout.route)
                    },
                    goBack = navController::popBackStack,
                )
            }
        }
        composable(SampleRoute.Checkout.route) {
            UiScreenTracer(
                space = SampleScreen.Checkout,
                owner = SampleOwner.DemoApp,
                viewName = "CheckoutScreen",
                dataReadyCondition = rememberDataReady(checkoutFeature.dataReady),
            ) {
                CheckoutScreen(
                    feature = checkoutFeature,
                    finish = {
                        navController.navigate(SampleRoute.Catalog.route) {
                            popUpTo(SampleRoute.Catalog.route) {
                                inclusive = true
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun CatalogScreen(
    feature: CatalogFeature,
    recommendationsFeature: RecommendationsFeature,
    openProduct: () -> Unit,
) {
    val state by feature.state.collectAsState()
    val dataReady by feature.dataReady.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        feature.loadInitialPage()
        recommendationsFeature.load()
    }

    TraceScreenContainer(
        title = "Catalog",
        target = TraceTargetUi(
            space = SampleScreen.Catalog,
            owner = SampleOwner.DemoApp,
            viewName = "CatalogScreen",
            dataReady = dataReady,
        ),
        stages = catalogStages(state),
        primaryAction = {
            ActionButton(
                text = "Reload",
                icon = Icons.Default.Refresh,
                onClick = {
                    scope.launch {
                        feature.loadInitialPage(force = true)
                        recommendationsFeature.load(force = true)
                    }
                },
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CatalogResultCard(state = state, openProduct = openProduct)
            RecommendationBlock(recommendationsFeature)
        }
    }
}

@Composable
private fun RecommendationBlock(feature: RecommendationsFeature) {
    val state by feature.state.collectAsState()
    val dataReady by feature.dataReady.collectAsState()
    val scope = rememberCoroutineScope()

    UiWidgetTracer(
        space = SampleScreen.Recommendations,
        owner = SampleOwner.DemoApp,
        viewName = "RecommendationsWidget",
        dataReadyCondition = rememberDataReady(feature.dataReady),
    ) {
        DemoCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = "Recommendations widget",
                            style = MaterialTheme.typography.titleSmall,
                            color = Ink,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "space=${SampleScreen.Recommendations.performanceName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Muted,
                        )
                    }
                    DataReadyBadge(ready = dataReady)
                }
                WidgetStages(ready = dataReady, loading = state is WidgetState.Loading)
                when (val current = state) {
                    WidgetState.Idle,
                    WidgetState.Loading -> LoadingState("recommendations.widget")
                    is WidgetState.Content -> Text(
                        text = current.items.joinToString(separator = "  •  "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Ink,
                    )
                }
                OutlinedActionButton(
                    text = "Reload widget",
                    icon = Icons.Default.Refresh,
                    onClick = {
                        scope.launch {
                            feature.load(force = true)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ProductScreen(
    feature: ProductFeature,
    openCheckout: () -> Unit,
    goBack: () -> Unit,
) {
    val state by feature.state.collectAsState()
    val dataReady by feature.dataReady.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        feature.loadProduct()
    }

    TraceScreenContainer(
        title = "Product",
        target = TraceTargetUi(
            space = SampleScreen.Product,
            owner = SampleOwner.DemoApp,
            viewName = "ProductScreen",
            dataReady = dataReady,
        ),
        stages = productStages(state),
        secondaryAction = {
            OutlinedActionButton(
                text = "Back",
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                onClick = goBack,
            )
        },
        primaryAction = {
            ActionButton(
                text = "Reload",
                icon = Icons.Default.Refresh,
                onClick = {
                    scope.launch {
                        feature.loadProduct(force = true)
                    }
                },
            )
        },
    ) {
        ProductDetailsCard(
            state = state,
            openCheckout = openCheckout,
        )
    }
}

@Composable
private fun CheckoutScreen(
    feature: CheckoutFeature,
    finish: () -> Unit,
) {
    val state by feature.state.collectAsState()
    val dataReady by feature.dataReady.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        feature.loadCheckout()
    }

    TraceScreenContainer(
        title = "Checkout",
        target = TraceTargetUi(
            space = SampleScreen.Checkout,
            owner = SampleOwner.DemoApp,
            viewName = "CheckoutScreen",
            dataReady = dataReady,
        ),
        stages = checkoutStages(state),
        primaryAction = {
            ActionButton(
                text = "Reload",
                icon = Icons.Default.Refresh,
                onClick = {
                    scope.launch {
                        feature.loadCheckout(force = true)
                    }
                },
            )
        },
    ) {
        CheckoutSummaryCard(
            state = state,
            finish = finish,
        )
    }
}

@Composable
private fun TraceScreenContainer(
    title: String,
    target: TraceTargetUi,
    stages: List<StageItem>,
    primaryAction: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    secondaryAction: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxSize()) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardSurface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Ink,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = target.viewName,
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                secondaryAction?.invoke()
                primaryAction()
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { StageTimeline(stages) }
            item { TraceTargetCard(target) }
            item { content() }
        }
    }
}

@Composable
private fun TraceTargetCard(target: TraceTargetUi) {
    DemoCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Trace target",
                    style = MaterialTheme.typography.titleSmall,
                    color = Ink,
                    fontWeight = FontWeight.SemiBold,
                )
                DataReadyBadge(ready = target.dataReady)
            }
            MetricLine(label = "space", value = target.space.performanceName)
            MetricLine(label = "owner", value = target.owner.performanceName)
            MetricLine(label = "view", value = target.viewName)
        }
    }
}

@Composable
private fun StageTimeline(stages: List<StageItem>) {
    DemoCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("Measured stages")
            stages.forEach { item ->
                StageRow(item)
            }
        }
    }
}

@Composable
private fun StageRow(item: StageItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StageMarker(item.status)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium,
                color = Ink,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.detail,
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        StatusBadge(item.status)
    }
}

@Composable
private fun WidgetStages(
    ready: Boolean,
    loading: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StageRow(
            StageItem(
                name = "NetworkRequestsTotalTime",
                detail = "recommendations.widget",
                status = stageStatus(ready = ready, loading = loading),
            ),
        )
        StageRow(
            StageItem(
                name = "MapToDomainTime",
                detail = "recommendations.widget",
                status = if (ready) StageStatus.Done else StageStatus.Pending,
            ),
        )
    }
}

@Composable
private fun StageMarker(status: StageStatus) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(status.softColor),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(status.color),
        )
    }
}

@Composable
private fun StatusBadge(status: StageStatus) {
    Badge(
        text = status.label,
        background = status.softColor,
        foreground = status.color,
    )
}

@Composable
private fun DataReadyBadge(ready: Boolean) {
    Badge(
        text = if (ready) "dataReady" else "waiting",
        background = if (ready) AccentSoft else WarningSoft,
        foreground = if (ready) Accent else Warning,
    )
}

@Composable
private fun Badge(
    text: String,
    background: Color,
    foreground: Color,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = foreground,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun MetricLine(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.widthIn(min = 52.dp),
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = Ink,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CatalogResultCard(
    state: CatalogState,
    openProduct: () -> Unit,
) {
    DemoCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("Catalog data")
            when (state) {
                CatalogState.Idle,
                CatalogState.Loading -> LoadingState("catalog.initial_page")
                is CatalogState.Content -> {
                    state.items.forEach { item ->
                        ProductRow(item)
                    }
                    ActionButton(
                        text = "Open product",
                        icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        onClick = openProduct,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProductDetailsCard(
    state: ProductState,
    openCheckout: () -> Unit,
) {
    DemoCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("Product payload")
            when (state) {
                ProductState.Idle,
                ProductState.Loading -> LoadingState("product.details")
                is ProductState.Content -> {
                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Ink,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = state.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Muted,
                    )
                    ActionButton(
                        text = "Checkout",
                        icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        onClick = openCheckout,
                    )
                }
            }
        }
    }
}

@Composable
private fun CheckoutSummaryCard(
    state: CheckoutState,
    finish: () -> Unit,
) {
    DemoCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("Checkout payload")
            when (state) {
                CheckoutState.Idle,
                CheckoutState.Loading -> LoadingState("checkout.summary")
                is CheckoutState.Content -> {
                    Text(
                        text = state.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Ink,
                    )
                    ActionButton(
                        text = "Finish",
                        icon = Icons.Default.Check,
                        onClick = finish,
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingState(requestName: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(PendingSoft)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "Loading",
            style = MaterialTheme.typography.bodyMedium,
            color = Ink,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = requestName,
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
        )
    }
}

@Composable
private fun ProductRow(item: CatalogItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(AppBackground)
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                color = Ink,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = item.priceText,
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
            )
        }
        Badge(
            text = item.badge,
            background = AccentSoft,
            foreground = Accent,
        )
    }
}

@Composable
private fun ActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Accent,
            contentColor = ButtonText,
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        modifier = Modifier.heightIn(min = 44.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(text = text, maxLines = 1)
    }
}

@Composable
private fun OutlinedActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Border),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = Ink,
            containerColor = CardSurface,
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        modifier = Modifier.heightIn(min = 44.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(text = text, maxLines = 1)
    }
}

@Composable
private fun DemoCard(content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, Border.copy(alpha = 0.65f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(modifier = Modifier.padding(14.dp)) {
            content()
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = Ink,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun LogsPanel(
    logs: List<String>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(LogBackground)
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Event log",
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
            )
            Badge(
                text = logs.size.toString(),
                background = Color(0xFF263244),
                foreground = Color(0xFFDCE3EA),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(logs) { line ->
                LogLine(line)
            }
        }
    }
}

@Composable
private fun LogLine(line: String) {
    val tag = line.substringBefore(" ")
    val body = line.removePrefix(tag).trimStart()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = tag,
            modifier = Modifier.widthIn(min = 48.dp),
            style = MaterialTheme.typography.labelSmall,
            color = logColor(tag),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFDCE3EA),
            fontFamily = FontFamily.Monospace,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun logColor(tag: String): Color = when (tag) {
    "SEND" -> Color(0xFF7DD3FC)
    "SDK" -> Color(0xFF86EFAC)
    "ACTION" -> Color(0xFFFDE68A)
    else -> Color(0xFFDCE3EA)
}

@Composable
private fun rememberDataReady(flow: StateFlow<Boolean>): DataReadyCondition =
    remember(flow) { DataReadyCondition.FlowDataReady(flow) }

private fun catalogStages(state: CatalogState): List<StageItem> {
    val ready = state is CatalogState.Content
    val loading = state is CatalogState.Loading
    return listOf(
        StageItem(
            name = "InitViewModelTime",
            detail = "CatalogFeature",
            status = StageStatus.Done,
        ),
        StageItem(
            name = "NetworkRequestsTotalTime",
            detail = "catalog.initial_page",
            status = stageStatus(ready = ready, loading = loading),
        ),
        StageItem(
            name = "MapToDomainTime",
            detail = "catalog.initial_page",
            status = if (ready) StageStatus.Done else StageStatus.Pending,
        ),
        StageItem(
            name = "PerformanceTrace",
            detail = "catalog_prepare_ui",
            status = if (ready) StageStatus.Done else StageStatus.Pending,
        ),
    )
}

private fun productStages(state: ProductState): List<StageItem> {
    val ready = state is ProductState.Content
    val loading = state is ProductState.Loading
    return listOf(
        StageItem(
            name = "NetworkRequestsTotalTime",
            detail = "product.details",
            status = stageStatus(ready = ready, loading = loading),
        ),
        StageItem(
            name = "MapToDomainTime",
            detail = "product.details",
            status = if (ready) StageStatus.Done else StageStatus.Pending,
        ),
    )
}

private fun checkoutStages(state: CheckoutState): List<StageItem> {
    val ready = state is CheckoutState.Content
    val loading = state is CheckoutState.Loading
    return listOf(
        StageItem(
            name = "NetworkRequestsTotalTime",
            detail = "checkout.summary",
            status = stageStatus(ready = ready, loading = loading),
        ),
        StageItem(
            name = "MapToDomainTime",
            detail = "checkout.summary",
            status = if (ready) StageStatus.Done else StageStatus.Pending,
        ),
    )
}

private fun stageStatus(
    ready: Boolean,
    loading: Boolean,
): StageStatus = when {
    ready -> StageStatus.Done
    loading -> StageStatus.Active
    else -> StageStatus.Pending
}

private class CatalogFeature(
    private val measure: PerformanceMeasure = PerformanceMeasure(),
) {
    private val _state = MutableStateFlow<CatalogState>(CatalogState.Idle)
    val state: StateFlow<CatalogState> = _state.asStateFlow()

    private val _dataReady = MutableStateFlow(false)
    val dataReady: StateFlow<Boolean> = _dataReady.asStateFlow()

    init {
        measure.saveTime(
            space = SampleScreen.Catalog,
            owner = SampleOwner.DemoApp,
            stage = PerformanceStage.InitViewModelTime("CatalogFeature"),
            timeMs = 4,
        )
    }

    suspend fun loadInitialPage(force: Boolean = false) {
        if (!force && _state.value is CatalogState.Content) return
        _dataReady.value = false
        _state.value = CatalogState.Loading

        val response = measure.measureSuspend(
            space = SampleScreen.Catalog,
            owner = SampleOwner.DemoApp,
            stage = PerformanceStage.NetworkRequestsTotalTime("catalog.initial_page"),
        ) {
            delay(450)
            CatalogResponse(
                products = listOf(
                    ProductDto("sku-1", "Smartphone", 1_200_000, "hit"),
                    ProductDto("sku-2", "Protective case", 80_000, "new"),
                    ProductDto("sku-3", "Fast charger", 130_000, "sale"),
                ),
            )
        }
        val model = measure.measureSuspend(
            space = SampleScreen.Catalog,
            owner = SampleOwner.DemoApp,
            stage = PerformanceStage.MapToDomainTime("catalog.initial_page"),
        ) {
            withContext(Dispatchers.Default) {
                delay(80)
                CatalogMapper.map(response)
            }
        }
        val prepared = measure.measureTraceSuspend(
            trace = PerformanceTrace(
                key = "catalog_prepare_ui",
                attributes = mapOf("screen" to SampleScreen.Catalog.performanceName),
            ),
        ) {
            withContext(Dispatchers.Default) {
                delay(30)
                model.copy(items = model.items.sortedBy { it.title })
            }
        }

        _state.value = CatalogState.Content(prepared.items)
        _dataReady.value = true
        SampleLogStore.add("ACTION Catalog dataReady=true")
    }
}

private class RecommendationsFeature(
    private val measure: PerformanceMeasure = PerformanceMeasure(),
) {
    private val _state = MutableStateFlow<WidgetState>(WidgetState.Idle)
    val state: StateFlow<WidgetState> = _state.asStateFlow()

    private val _dataReady = MutableStateFlow(false)
    val dataReady: StateFlow<Boolean> = _dataReady.asStateFlow()

    suspend fun load(force: Boolean = false) {
        if (!force && _state.value is WidgetState.Content) return
        _dataReady.value = false
        _state.value = WidgetState.Loading

        val response = measure.measureSuspend(
            space = SampleScreen.Recommendations,
            owner = SampleOwner.DemoApp,
            stage = PerformanceStage.NetworkRequestsTotalTime("recommendations.widget"),
        ) {
            delay(320)
            listOf("Headphones", "Power bank", "USB-C cable")
        }
        val items = measure.measureSuspend(
            space = SampleScreen.Recommendations,
            owner = SampleOwner.DemoApp,
            stage = PerformanceStage.MapToDomainTime("recommendations.widget"),
        ) {
            withContext(Dispatchers.Default) {
                delay(40)
                response.map { it.uppercase() }
            }
        }

        _state.value = WidgetState.Content(items)
        _dataReady.value = true
        SampleLogStore.add("ACTION Recommendations dataReady=true")
    }
}

private class ProductFeature(
    private val measure: PerformanceMeasure = PerformanceMeasure(),
) {
    private val _state = MutableStateFlow<ProductState>(ProductState.Idle)
    val state: StateFlow<ProductState> = _state.asStateFlow()

    private val _dataReady = MutableStateFlow(false)
    val dataReady: StateFlow<Boolean> = _dataReady.asStateFlow()

    fun reset() {
        _state.value = ProductState.Idle
        _dataReady.value = false
    }

    suspend fun loadProduct(force: Boolean = false) {
        if (!force && _state.value is ProductState.Content) return
        _dataReady.value = false
        _state.value = ProductState.Loading

        val dto = measure.measureSuspend(
            space = SampleScreen.Product,
            owner = SampleOwner.DemoApp,
            stage = PerformanceStage.NetworkRequestsTotalTime("product.details"),
        ) {
            delay(380)
            ProductDetailsDto(
                title = "Smartphone",
                description = "Fast screen, large battery, reliable daily performance.",
            )
        }
        val model = measure.measureSuspend(
            space = SampleScreen.Product,
            owner = SampleOwner.DemoApp,
            stage = PerformanceStage.MapToDomainTime("product.details"),
        ) {
            withContext(Dispatchers.Default) {
                delay(55)
                ProductState.Content(dto.title, dto.description)
            }
        }

        _state.value = model
        _dataReady.value = true
        SampleLogStore.add("ACTION Product dataReady=true")
    }
}

private class CheckoutFeature(
    private val measure: PerformanceMeasure = PerformanceMeasure(),
) {
    private val _state = MutableStateFlow<CheckoutState>(CheckoutState.Idle)
    val state: StateFlow<CheckoutState> = _state.asStateFlow()

    private val _dataReady = MutableStateFlow(false)
    val dataReady: StateFlow<Boolean> = _dataReady.asStateFlow()

    fun reset() {
        _state.value = CheckoutState.Idle
        _dataReady.value = false
    }

    suspend fun loadCheckout(force: Boolean = false) {
        if (!force && _state.value is CheckoutState.Content) return
        _dataReady.value = false
        _state.value = CheckoutState.Loading

        val summary = measure.measureSuspend(
            space = SampleScreen.Checkout,
            owner = SampleOwner.DemoApp,
            stage = PerformanceStage.NetworkRequestsTotalTime("checkout.summary"),
        ) {
            delay(290)
            "1 item, delivery tomorrow, total 1 200 000 UZS"
        }
        val model = measure.measureSuspend(
            space = SampleScreen.Checkout,
            owner = SampleOwner.DemoApp,
            stage = PerformanceStage.MapToDomainTime("checkout.summary"),
        ) {
            withContext(Dispatchers.Default) {
                delay(35)
                CheckoutState.Content(summary)
            }
        }

        _state.value = model
        _dataReady.value = true
        SampleLogStore.add("ACTION Checkout dataReady=true")
    }
}

private object CatalogMapper {
    fun map(response: CatalogResponse): CatalogModel = CatalogModel(
        items = response.products.map { product ->
            CatalogItem(
                id = product.id,
                title = product.title,
                priceText = "${product.price} UZS",
                badge = product.badge,
            )
        },
    )
}

private data class TraceTargetUi(
    val space: SampleScreen,
    val owner: SampleOwner,
    val viewName: String,
    val dataReady: Boolean,
)

private data class StageItem(
    val name: String,
    val detail: String,
    val status: StageStatus,
)

private enum class StageStatus(
    val label: String,
    val color: Color,
    val softColor: Color,
) {
    Done("sent", Accent, AccentSoft),
    Active("running", Warning, WarningSoft),
    Pending("waiting", Muted, PendingSoft),
}

private enum class SampleRoute(
    val route: String,
    val title: String,
    val number: Int,
) {
    Catalog("catalog", "Catalog", 1),
    Product("product", "Product", 2),
    Checkout("checkout", "Checkout", 3);

    companion object {
        fun fromRoute(route: String?): SampleRoute =
            entries.firstOrNull { it.route == route } ?: Catalog
    }
}

private enum class SampleOwner(
    override val performanceName: String,
) : PerformanceOwner {
    DemoApp("DemoApp"),
}

private enum class SampleScreen(
    override val performanceName: String,
) : PerformanceSpace {
    Catalog("Catalog"),
    Product("Product"),
    Checkout("Checkout"),
    Recommendations("Recommendations"),
}

private sealed interface CatalogState {
    data object Idle : CatalogState
    data object Loading : CatalogState
    data class Content(val items: List<CatalogItem>) : CatalogState
}

private sealed interface WidgetState {
    data object Idle : WidgetState
    data object Loading : WidgetState
    data class Content(val items: List<String>) : WidgetState
}

private sealed interface ProductState {
    data object Idle : ProductState
    data object Loading : ProductState
    data class Content(val title: String, val description: String) : ProductState
}

private sealed interface CheckoutState {
    data object Idle : CheckoutState
    data object Loading : CheckoutState
    data class Content(val summary: String) : CheckoutState
}

private data class CatalogResponse(val products: List<ProductDto>)
private data class ProductDto(val id: String, val title: String, val price: Long, val badge: String)
private data class ProductDetailsDto(val title: String, val description: String)
private data class CatalogModel(val items: List<CatalogItem>)
private data class CatalogItem(val id: String, val title: String, val priceText: String, val badge: String)
