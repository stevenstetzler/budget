package com.vidalabs.budget.ui.onboarding

import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Icons.Filled.AttachMoney: ImageVector
    get() {
        if (_attachMoney != null) {
            return _attachMoney!!
        }
        _attachMoney = ImageVector.Builder(
            name = "Filled.AttachMoney",
            defaultWidth = 24.0.dp,
            defaultHeight = 24.0.dp,
            viewportWidth = 24.0f,
            viewportHeight = 24.0f
        ).apply {
            path(
                fill = SolidColor(Color(0xFF000000)),
                fillAlpha = 1.0f,
                strokeAlpha = 1.0f,
                strokeLineWidth = 0.0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4.0f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(11.8f, 10.9f)
                curveToRelative(-2.27f, -0.59f, -3.0f, -1.2f, -3.0f, -2.15f)
                curveToRelative(0.0f, -1.09f, 1.01f, -1.85f, 2.7f, -1.85f)
                curveToRelative(1.78f, 0.0f, 2.44f, 0.85f, 2.5f, 2.1f)
                horizontalLineTo(16.1f)
                curveToRelative(-0.07f, -1.72f, -1.1f, -3.3f, -3.1f, -3.77f)
                verticalLineTo(3.5f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(1.72f)
                curveToRelative(-1.73f, 0.37f, -3.12f, 1.46f, -3.12f, 3.23f)
                curveToRelative(0.0f, 1.94f, 1.62f, 2.91f, 4.2f, 3.53f)
                curveToRelative(2.52f, 0.61f, 3.0f, 1.34f, 3.0f, 2.34f)
                curveToRelative(0.0f, 0.7f, -0.51f, 1.85f, -2.7f, 1.85f)
                curveToRelative(-2.07f, 0.0f, -2.84f, -0.95f, -2.94f, -2.1f)
                horizontalLineTo(7.11f)
                curveToRelative(0.12f, 1.98f, 1.54f, 3.12f, 3.39f, 3.55f)
                verticalLineTo(20.5f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(-1.73f)
                curveToRelative(1.75f, -0.34f, 3.15f, -1.33f, 3.15f, -3.22f)
                curveToRelative(0.0f, -2.35f, -1.62f, -3.06f, -3.85f, -3.65f)
                close()
            }
        }.build()
        return _attachMoney!!
    }

private var _attachMoney: ImageVector? = null
