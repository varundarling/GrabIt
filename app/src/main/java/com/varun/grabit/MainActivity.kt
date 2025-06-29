package com.varun.grabit

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.varun.grabit.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    val items = mutableListOf<Items>()
    private lateinit var adapter: ItemAdapter
    private lateinit var database: DatabaseReference
    lateinit var binding: ActivityMainBinding
    private lateinit var mAdView1: AdView
    private var valueEventListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        val requestConfig = RequestConfiguration.Builder()
            .setTagForChildDirectedTreatment(RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE)
            .build()

        MobileAds.setRequestConfiguration(requestConfig)

        MobileAds.initialize(this) { }
        mAdView1 = findViewById(R.id.bannerAdView)
        val adRequest = AdRequest.Builder().build()
        mAdView1.loadAd(adRequest)

        database = FirebaseDatabase.getInstance().getReference("items")

        setSupportActionBar(binding.toolbar)

        adapter = ItemAdapter(
            items,
            onItemChecked = { _, position, isChecked -> updateItemCheckedStatus(position, isChecked) },
            onItemDeleted = { item, _ -> deleteItem(item.id) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        loadItems()

        binding.fabAddItem.setOnClickListener {
            showAddItemBottomSheet()
        }

        updateTotalAmount()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    @SuppressLint("InflateParams")
    private fun  showAddItemBottomSheet() {
        val bottomSheetDialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_add_item, null)
        bottomSheetDialog.setContentView(view)

        val editTextItemName = view.findViewById<EditText>(R.id.editItemName)
        val editTextQuantity = view.findViewById<EditText>(R.id.editQuantity)
        val editTextPrice = view.findViewById<EditText>(R.id.editPrice)
        val buttonAdd = view.findViewById<Button>(R.id.buttonAdd)

        buttonAdd.setOnClickListener {
            val name = editTextItemName.text.toString()
            val quantity = editTextQuantity.text.toString().toIntOrNull() ?: 1
            val price = editTextPrice.text.toString().toDoubleOrNull() ?: 0.0

            if(name.isNotEmpty()){
                val itemId = database.push().key ?: return@setOnClickListener
                val item = Items(itemId, name, quantity, price)
                database.child(itemId).setValue(item)
                    .addOnSuccessListener {
                        bottomSheetDialog.dismiss()
                    }
                    .addOnFailureListener{
                        editTextItemName.error = "Failed to add item"
                    }
            } else{
                editTextItemName.error = "Please enter an item name"
            }
        }
        bottomSheetDialog.show()
    }

    private fun loadItems() {
        valueEventListener?.let { database.removeEventListener(it) }
        valueEventListener = object : ValueEventListener{
            override fun onDataChange(snapshot: DataSnapshot) {
                val newItems = mutableListOf<Items>()
                for (data in snapshot.children) {
                    try {
                        val item = data.getValue(Items::class.java)
                        item?.let {
                            newItems.add(it)
                            android.util.Log.d("MainActivity", "Loaded item: $it")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "Error deserializing item: ${data.key}, ${e.message}")
                    }
                }
                android.util.Log.d("MainActivity", "Total items loaded: ${newItems.size}")
                adapter.updateItems(newItems)
                updateTotalAmount()
            }

            override fun onCancelled(error: DatabaseError) {
                Snackbar.make(binding.root, "Failed to load items: ${error.message}", Snackbar.LENGTH_SHORT).show()
            }
        }
        valueEventListener?.let { database.addValueEventListener(it) }
    }

    private fun updateItemCheckedStatus(position: Int, isChecked: Boolean) {
        if (position < 0 || position >= items.size) {
            android.util.Log.e("MainActivity", "Invalid position: $position, items size: ${items.size}")
            return
        }
        val item = items[position]
        if (item.id.isEmpty()) {
            android.util.Log.e("MainActivity", "Invalid item ID at position: $position")
            return
        }
        item.isChecked = isChecked
        database.child(item.id).child("isChecked").setValue(isChecked)
            .addOnFailureListener { e ->
                android.util.Log.e("MainActivity", "Failed to update isChecked for ${item.id}: ${e.message}")
                Snackbar.make(binding.root, "Failed to update item status", Snackbar.LENGTH_SHORT).show()
                // Revert local change if Firebase fails
                item.isChecked = !isChecked
                adapter.notifyItemChanged(position)
            }
        updateTotalAmount()
    }

    private fun deleteItem(itemId: String) {
        if (itemId.isEmpty()) {
            android.util.Log.e("MainActivity", "Invalid item ID for deletion")
            return
        }
        val position = items.indexOfFirst { it.id == itemId }
        if (position == -1) {
            android.util.Log.e("MainActivity", "Item with ID $itemId not found in list")
            return
        }
        val item = items[position]
        android.app.AlertDialog.Builder(this)
            .setTitle("Delete Item")
            .setMessage("Are you sure you want to delete ${item.name}?")
            .setPositiveButton("Delete") { _, _ ->
                valueEventListener?.let { database.removeEventListener(it) } // Prevent race condition
                database.child(itemId).removeValue()
                    .addOnSuccessListener {
                        android.util.Log.d("MainActivity", "Item deleted from Firebase: $itemId")
                        items.removeAt(position)
                        adapter.notifyItemRemoved(position)
                        updateTotalAmount()
                        Snackbar.make(binding.root, "Item deleted", Snackbar.LENGTH_SHORT).show()
                        loadItems() // Re-attach listener
                    }
                    .addOnFailureListener { e ->
                        android.util.Log.e("MainActivity", "Failed to delete item $itemId: ${e.message}")
                        Snackbar.make(binding.root, "Failed to delete item", Snackbar.LENGTH_SHORT).show()
                        loadItems() // Re-attach listener
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    @SuppressLint("DefaultLocale")
    fun updateTotalAmount(){
        val total = items.filter { it.isChecked }.sumOf {  it.price * it.quantity }
        binding.totalAmount.text = String.format("$%.2f", total)
    }

    override fun onDestroy() {
        super.onDestroy()
        valueEventListener?.let { database.removeEventListener(it) } // Clean up listener
    }
}