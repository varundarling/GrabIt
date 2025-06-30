package com.varun.grabit

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
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
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.varun.grabit.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private val items = mutableListOf<Items>()
    private lateinit var adapter: ItemAdapter
    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private var valueEventListener: ValueEventListener? = null
    private var lastUpdateTimestamp: Long = 0
    lateinit var binding: ActivityMainBinding
    private lateinit var mAdView1: AdView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        // Check Google Play Services
        val googleApi = GoogleApiAvailability.getInstance()
        val resultCode = googleApi.isGooglePlayServicesAvailable(this)
        if (resultCode != com.google.android.gms.common.ConnectionResult.SUCCESS) {
            Log.e("MainActivity", "Google Play Services unavailable, code: $resultCode")
            Snackbar.make(binding.root, "Google Play Services unavailable. Please update.", Snackbar.LENGTH_LONG).show()
        }

        // Enable Firebase persistence
        try {
            FirebaseDatabase.getInstance().setPersistenceEnabled(true)
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to enable Firebase persistence: ${e.message}")
        }

        auth = FirebaseAuth.getInstance()
        setupAuthentication()

        val requestConfig = RequestConfiguration.Builder()
            .setTagForChildDirectedTreatment(RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE)
            .build()

        MobileAds.setRequestConfiguration(requestConfig)

        MobileAds.initialize(this) { }
        mAdView1 = findViewById(R.id.bannerAdView)
        val adRequest = AdRequest.Builder().build()
        mAdView1.loadAd(adRequest)
        mAdView1.setAdListener(object : com.google.android.gms.ads.AdListener() {
            override fun onAdFailedToLoad(adError: com.google.android.gms.ads.LoadAdError) {
                Log.e("AdMob", "Ad failed to load: ${adError.message}, code: ${adError.code}")
            }
        })

        setSupportActionBar(binding.toolbar)

        adapter = ItemAdapter(
            items,
            onItemChecked = { item, position, isChecked -> updateItemCheckedStatus(item.id, isChecked, position) },
            onItemDeleted = { item, _ -> deleteItem(item.id) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupAuthentication() {
        auth.signInAnonymously().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val uid = auth.currentUser?.uid
                Log.d("MainActivity", "Anonymous auth successful, UID: $uid")
                initializeDatabase(uid)
            } else {
                Log.e("MainActivity", "Anonymous auth failed: ${task.exception?.message}, code: ${task.exception?.let { it.cause?.toString() }}")
                val snackbar = Snackbar.make(
                    binding.root,
                    "Authentication failed: ${task.exception?.message}. Check network or retry?",
                    Snackbar.LENGTH_INDEFINITE
                )
                snackbar.setAction("Retry") { setupAuthentication() }
                snackbar.show()
                // Fallback to offline/debug mode
                initializeDatabase("offline_debug") // Use for debugging only
            }
        }
    }

    private fun initializeDatabase(userId: String?) {
        if (userId == null) {
            Log.e("MainActivity", "User ID is null, cannot initialize database")
            val snackbar = Snackbar.make(
                binding.root,
                "Cannot connect to database. Retry?",
                Snackbar.LENGTH_INDEFINITE
            )
            snackbar.setAction("Retry") { setupAuthentication() }
            snackbar.show()
            return
        }
        database = FirebaseDatabase.getInstance().getReference("items").child(userId)
        database.keepSynced(true)
        Log.d("MainActivity", "Database initialized with path: items/$userId")
        loadItems()
        binding.fabAddItem.setOnClickListener {
            showAddItemBottomSheet()
        }
        updateTotalAmount()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_privacy_policy -> {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://grabitvarun.blogspot.com/2025/06/privacy-policy-grabit-shopping-list.html")))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    @SuppressLint("InflateParams")
    private fun showAddItemBottomSheet() {
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

            if (name.isNotEmpty()) {
                val itemId = database.push().key ?: return@setOnClickListener
                val item = Items(itemId, name, quantity, price)
                Log.d("MainActivity", "Attempting to add item: $item to path: items/${auth.currentUser?.uid ?: "offline_debug"}/$itemId")
                database.child(itemId).setValue(item)
                    .addOnSuccessListener {
                        bottomSheetDialog.dismiss()
                        Snackbar.make(binding.root, "Item added", Snackbar.LENGTH_SHORT).show()
                        loadItems()
                    }
                    .addOnFailureListener { e ->
                        Log.e("MainActivity", "Failed to add item: ${e.message}")
                        editTextItemName.error = "Failed to add item: ${e.message}"
                    }
            } else {
                editTextItemName.error = "Please enter an item name"
            }
        }
        bottomSheetDialog.show()
    }

    private fun loadItems() {
        valueEventListener?.let { database.removeEventListener(it) }
        valueEventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastUpdateTimestamp < 500) {
                    Log.d("MainActivity", "Skipping loadItems due to recent update")
                    return
                }
                lastUpdateTimestamp = currentTime
                val newItems = mutableListOf<Items>()
                for (data in snapshot.children) {
                    try {
                        val item = data.getValue(Items::class.java)
                        item?.let {
                            if (it.id.isNotEmpty() && it.id == data.key) {
                                newItems.add(it)
                                Log.d("MainActivity", "Loaded item: $it")
                            } else {
                                Log.e("MainActivity", "Invalid item ID: ${it.id}, expected: ${data.key}")
                                data.key?.let { key -> database.child(key).removeValue() }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Error deserializing item: ${data.key}, ${e.message}")
                    }
                }
                Log.d("MainActivity", "Total items loaded: ${newItems.size}")
                adapter.updateItems(newItems)
                binding.noItemsText.visibility = if (newItems.isEmpty()) View.VISIBLE else View.GONE
                updateTotalAmount()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("MainActivity", "Failed to load items: ${error.message}, code: ${error.code}")
                Snackbar.make(binding.root, "Failed to load items: ${error.message}", Snackbar.LENGTH_SHORT).show()
            }
        }
        valueEventListener?.let { database.addValueEventListener(it) }
    }

    private fun updateItemCheckedStatus(itemId: String, isChecked: Boolean, position: Int) {
        if (itemId.isEmpty() || itemId == "0") {
            Log.e("MainActivity", "Invalid item ID for checkbox update")
            Snackbar.make(binding.root, "Error: Invalid item ID", Snackbar.LENGTH_SHORT).show()
            return
        }
        if (position < 0 || position >= items.size) {
            Log.e("MainActivity", "Invalid position: $position, items size: ${items.size}")
            Snackbar.make(binding.root, "Error: Item not found", Snackbar.LENGTH_SHORT).show()
            return
        }
        val item = items[position]
        if (item.id != itemId) {
            Log.e("MainActivity", "ID mismatch at position $position: expected $itemId, found ${item.id}")
            Snackbar.make(binding.root, "Error: Item ID mismatch", Snackbar.LENGTH_SHORT).show()
            return
        }
        Log.d("MainActivity", "Updating ${item.name} with ID $itemId to isChecked=$isChecked")
        valueEventListener?.let { database.removeEventListener(it) }
        item.isChecked = isChecked
        adapter.notifyItemChanged(position)
        updateTotalAmount()
        database.child(itemId).child("isChecked").setValue(isChecked)
            .addOnSuccessListener {
                Log.d("MainActivity", "Successfully updated isChecked for $itemId")
            }
            .addOnFailureListener { e ->
                Log.e("MainActivity", "Failed to update isChecked for $itemId: ${e.message}")
                Snackbar.make(binding.root, "Failed to update item status: ${e.message}", Snackbar.LENGTH_SHORT).show()
                loadItems()
            }
    }

    private fun deleteItem(itemId: String) {
        if (itemId.isEmpty() || itemId == "0") {
            Log.e("MainActivity", "Invalid item ID for deletion: $itemId")
            Snackbar.make(binding.root, "Error: Invalid item ID", Snackbar.LENGTH_SHORT).show()
            return
        }
        val position = items.indexOfFirst { it.id == itemId }
        if (position == -1) {
            Log.e("MainActivity", "Item with ID $itemId not found in list")
            Snackbar.make(binding.root, "Error: Item not found", Snackbar.LENGTH_SHORT).show()
            return
        }
        val item = items[position]
        android.app.AlertDialog.Builder(this)
            .setTitle("Delete Item")
            .setMessage("Are you sure you want to delete ${item.name}?")
            .setPositiveButton("Delete") { _, _ ->
                valueEventListener?.let { database.removeEventListener(it) }
                database.child(itemId).removeValue()
                    .addOnSuccessListener {
                        Log.d("MainActivity", "Item deleted from Firebase: $itemId")
                        items.removeAt(position)
                        adapter.notifyItemRemoved(position)
                        binding.noItemsText.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                        updateTotalAmount()
                        Snackbar.make(binding.root, "Item deleted", Snackbar.LENGTH_SHORT).show()
                        loadItems()
                    }
                    .addOnFailureListener { e ->
                        Log.e("MainActivity", "Failed to delete item $itemId: ${e.message}")
                        Snackbar.make(binding.root, "Failed to delete item: ${e.message}", Snackbar.LENGTH_SHORT).show()
                        loadItems()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    @SuppressLint("DefaultLocale")
    fun updateTotalAmount() {
        val total = items.filter { it.isChecked }.sumOf { it.price * it.quantity }
        Log.d("MainActivity", "Updating total: $total, checked items: ${items.filter { it.isChecked }.map { it.name }}")
        binding.totalAmount.text = String.format("%.2f", total)
    }

    override fun onDestroy() {
        super.onDestroy()
        valueEventListener?.let { database.removeEventListener(it) }
    }
}