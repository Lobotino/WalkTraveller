package ru.lobotino.walktraveller

import android.graphics.Color
import android.os.Bundle
import android.preference.PreferenceManager
import android.widget.LinearLayout
import android.widget.RelativeLayout.LayoutParams
import androidx.appcompat.app.AppCompatActivity
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.ItemizedOverlay
import org.osmdroid.views.overlay.OverlayItem
import org.osmdroid.views.overlay.PathOverlay


class MainActivity : AppCompatActivity() {
    private var mMapView: MapView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance()
            .load(baseContext, PreferenceManager.getDefaultSharedPreferences(baseContext));

        setContentView(R.layout.activity_main)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setDisplayShowHomeEnabled(true)
        val mapContainer = findViewById<LinearLayout>(R.id.map_container)
        mMapView = MapView(this).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.ALWAYS)

            controller.apply {
                setZoom(13.0)
            }

            val mIncr = 10000
            val gPt0 = GeoPoint(51500000, -150000)
            val gPt1 = GeoPoint(gPt0.latitudeE6 + mIncr, gPt0.longitudeE6)
            val gPt2 = GeoPoint(gPt0.latitudeE6 + mIncr, gPt0.longitudeE6 + mIncr)
            val gPt3 = GeoPoint(gPt0.latitudeE6, gPt0.longitudeE6 + mIncr)
            controller.setCenter(gPt0)
            val myPath = PathOverlay(Color.RED, this@MainActivity)
            myPath.addPoint(gPt0)
            myPath.addPoint(gPt1)
            myPath.addPoint(gPt2)
            myPath.addPoint(gPt3)
            myPath.addPoint(gPt0)
            overlays.add(myPath)
        }
//        mMapView!!.isTilesScaledToDpi = true
        mapContainer.addView(
            mMapView, LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
        )
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    override fun onPause() {
        super.onPause()
        mMapView?.onPause()
    }

    override fun onResume() {
        super.onResume()
        mMapView?.onResume()
    }
}
