package uk.tojoco.gpstracker.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import uk.tojoco.gpstracker.data.LocationEntity
import uk.tojoco.gpstracker.databinding.ItemLocationBinding
import java.text.SimpleDateFormat
import java.util.*

class LocationAdapter(private val locations: List<LocationEntity>) :
    RecyclerView.Adapter<LocationAdapter.LocationViewHolder>() {

    class LocationViewHolder(val binding: ItemLocationBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LocationViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemLocationBinding.inflate(inflater, parent, false)
        return LocationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LocationViewHolder, position: Int) {
        val item = locations[position]
        holder.binding.latlngText.text = "Lat: ${item.latitude}, Lng: ${item.longitude}"
        holder.binding.timestampText.text = SimpleDateFormat("HH:mm:ss dd/MM/yyyy", Locale.getDefault())
            .format(Date(item.timestamp))
    }

    override fun getItemCount() = locations.size
}
