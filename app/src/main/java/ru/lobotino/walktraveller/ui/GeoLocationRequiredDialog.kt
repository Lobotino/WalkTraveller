package ru.lobotino.walktraveller.ui

import android.content.Context
import ru.lobotino.walktraveller.R

class GeoLocationRequiredDialog(context: Context, onConfirm: (() -> Unit)?) : ConfirmInfoDialog(
    context,
    context.getString(R.string.geo_location_required_dialog_title),
    context.getString(R.string.geo_location_required_dialog_description),
    onConfirm
)
