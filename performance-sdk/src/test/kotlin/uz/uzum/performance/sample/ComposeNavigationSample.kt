package uz.uzum.performance.sample

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import kotlinx.coroutines.flow.Flow
import uz.uzum.performance.api.PerformanceOwner
import uz.uzum.performance.api.PerformanceSpace
import uz.uzum.performance.tracing.DataReadyCondition
import uz.uzum.performance.compose.UiScreenTracer

@Composable
internal fun SampleComposeNavigation(
    navController: NavHostController,
    dataReady: SampleDataReadyFlows,
) {
    NavHost(
        navController = navController,
        startDestination = SampleComposeRoute.Main.route,
    ) {
        sampleScreen(
            route = SampleComposeRoute.Main,
            dataReady = dataReady.main,
        ) {
            MainSampleScreen(
                openProduct = { navController.navigate(SampleComposeRoute.Product.route) },
            )
        }
        sampleScreen(
            route = SampleComposeRoute.Product,
            dataReady = dataReady.product,
        ) {
            ProductSampleScreen(
                openCheckout = { navController.navigate(SampleComposeRoute.Checkout.route) },
                goBack = { navController.popBackStack() },
            )
        }
        sampleScreen(
            route = SampleComposeRoute.Checkout,
            dataReady = dataReady.checkout,
        ) {
            CheckoutSampleScreen(
                finishCheckout = {
                    navController.navigate(SampleComposeRoute.Main.route) {
                        popUpTo(SampleComposeRoute.Main.route) {
                            inclusive = true
                        }
                    }
                },
            )
        }
    }
}

internal data class SampleDataReadyFlows(
    val main: Flow<Boolean>,
    val product: Flow<Boolean>,
    val checkout: Flow<Boolean>,
)

internal enum class SampleComposeRoute(
    val route: String,
    override val performanceName: String,
    val viewName: String,
) : PerformanceSpace {
    Main("main", "Main", "MainScreen"),
    Product("product", "Product", "ProductScreen"),
    Checkout("checkout", "Checkout", "CheckoutScreen"),
}

internal object SampleComposeOwner : PerformanceOwner {
    override val performanceName: String = "SampleComposeApp"
}

private fun NavGraphBuilder.sampleScreen(
    route: SampleComposeRoute,
    dataReady: Flow<Boolean>,
    content: @Composable () -> Unit,
) {
    composable(route.route) {
        UiScreenTracer(
            space = route,
            owner = SampleComposeOwner,
            viewName = route.viewName,
            dataReadyCondition = DataReadyCondition.FlowDataReady(dataReady),
            content = content,
        )
    }
}

@Composable
private fun MainSampleScreen(
    openProduct: () -> Unit,
) {
    SampleActionSink(openProduct)
}

@Composable
private fun ProductSampleScreen(
    openCheckout: () -> Unit,
    goBack: () -> Unit,
) {
    SampleActionSink(openCheckout, goBack)
}

@Composable
private fun CheckoutSampleScreen(
    finishCheckout: () -> Unit,
) {
    SampleActionSink(finishCheckout)
}

@Composable
private fun SampleActionSink(
    vararg actions: () -> Unit,
) {
    LaunchedEffect(actions.size) {
        // Real screens would call these lambdas from buttons, list items, or effects.
    }
}
