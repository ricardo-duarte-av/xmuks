package pt.aguiarvieira.xmuks.core.designsystem.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import pt.aguiarvieira.xmuks.core.designsystem.R

private val Weights = listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)

/**
 * Google Sans Flex, bundled as one variable font (OFL; licence in assets/licenses). Each weight is
 * the same file instanced through the `wght` axis; [roundness] drives the `ROND` axis (0–100).
 */
private fun googleSansFlex(roundness: Float) =
    FontFamily(
        Weights.map { weight ->
            Font(
                resId = R.font.google_sans_flex,
                weight = weight,
                variationSettings =
                    FontVariation.Settings(
                        FontVariation.weight(weight.weight),
                        FontVariation.Setting("ROND", roundness),
                    ),
            )
        },
    )

/** Body and default text: the standard, square-shouldered cut. */
val GoogleSansFlex = googleSansFlex(roundness = 0f)

/** Fully rounded cut, used by the emphasized styles (headlines, titles in hero moments). */
val GoogleSansFlexRounded = googleSansFlex(roundness = 100f)

private fun TextStyle.regular() = copy(fontFamily = GoogleSansFlex)

private fun TextStyle.rounded() = copy(fontFamily = GoogleSansFlexRounded)

private val Base = Typography()

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val XmuksTypography =
    Typography(
        displayLarge = Base.displayLarge.regular(),
        displayMedium = Base.displayMedium.regular(),
        displaySmall = Base.displaySmall.regular(),
        headlineLarge = Base.headlineLarge.regular(),
        headlineMedium = Base.headlineMedium.regular(),
        headlineSmall = Base.headlineSmall.regular(),
        titleLarge = Base.titleLarge.regular(),
        titleMedium = Base.titleMedium.regular(),
        titleSmall = Base.titleSmall.regular(),
        bodyLarge = Base.bodyLarge.regular(),
        bodyMedium = Base.bodyMedium.regular(),
        bodySmall = Base.bodySmall.regular(),
        labelLarge = Base.labelLarge.regular(),
        labelMedium = Base.labelMedium.regular(),
        labelSmall = Base.labelSmall.regular(),
        displayLargeEmphasized = Base.displayLargeEmphasized.rounded(),
        displayMediumEmphasized = Base.displayMediumEmphasized.rounded(),
        displaySmallEmphasized = Base.displaySmallEmphasized.rounded(),
        headlineLargeEmphasized = Base.headlineLargeEmphasized.rounded(),
        headlineMediumEmphasized = Base.headlineMediumEmphasized.rounded(),
        headlineSmallEmphasized = Base.headlineSmallEmphasized.rounded(),
        titleLargeEmphasized = Base.titleLargeEmphasized.rounded(),
        titleMediumEmphasized = Base.titleMediumEmphasized.rounded(),
        titleSmallEmphasized = Base.titleSmallEmphasized.rounded(),
        bodyLargeEmphasized = Base.bodyLargeEmphasized.regular(),
        bodyMediumEmphasized = Base.bodyMediumEmphasized.regular(),
        bodySmallEmphasized = Base.bodySmallEmphasized.regular(),
        labelLargeEmphasized = Base.labelLargeEmphasized.regular(),
        labelMediumEmphasized = Base.labelMediumEmphasized.regular(),
        labelSmallEmphasized = Base.labelSmallEmphasized.regular(),
    )
