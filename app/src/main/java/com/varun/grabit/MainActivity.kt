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

    // **CRITICAL FIX: Missing loadItems() method implementation**
    private fun loadItems() {
        Log.d("MainActivity", "Loading items from Firebase")

        // Remove any existing listener to prevent duplicates
        valueEventListener?.let { listener ->
            database.removeEventListener(listener)
        }

        valueEventListener = object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                Log.d("MainActivity", "onDataChange triggered: ${dataSnapshot.childrenCount} items")

                val itemsList = mutableListOf<Items>()

                try {
                    for (itemSnapshot in dataSnapshot.children) {
                        val item = itemSnapshot.getValue(Items::class.java)
                        if (item != null) {
                            itemsList.add(item)
                            Log.d("MainActivity", "Loaded item: ${item.name}")
                        } else {
                            Log.w("MainActivity", "Failed to parse item from snapshot: ${itemSnapshot.key}")
                        }
                    }

                    // Update UI on main thread
                    runOnUiThread {
                        items.clear()
                        items.addAll(itemsList)
                        adapter.notifyDataSetChanged()
                        updateTotalAmount()

                        Log.d("MainActivity", "UI updated with ${items.size} items")

                        // Show/hide empty state
                        if (items.isEmpty()) {
                            binding.recyclerView.visibility = View.GONE
                            // You can add an empty state view here if needed
                        } else {
                            binding.recyclerView.visibility = View.VISIBLE
                        }
                    }

                } catch (e: Exception) {
                    Log.e("MainActivity", "Error processing data snapshot: ${e.message}", e)
                    runOnUiThread {
                        Snackbar.make(
                            binding.root,
                            "Error loading data: ${e.message}",
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Log.e("MainActivity", "loadItems:onCancelled", databaseError.toException())
                runOnUiThread {
                    Snackbar.make(
                        binding.root,
                        "Failed to load data: ${databaseError.message}. Check your internet connection.",
                        Snackbar.LENGTH_INDEFINITE
                    ).setAction("Retry") {
                        loadItems()
                    }.show()
                }
            }
        }

        database.addValueEventListener(valueEventListener!!)
    }

    // **FIX: Complete updateItemCheckedStatus() method**
    private fun updateItemCheckedStatus(itemId: String, isChecked: Boolean, position: Int) {
        Log.d("MainActivity", "Updating item $itemId checked status to $isChecked")

        database.child(itemId).child("isChecked").setValue(isChecked)
            .addOnSuccessListener {
                Log.d("MainActivity", "Item checked status updated successfully")
                // Update local data
                items.find { it.id == itemId }?.isChecked = isChecked
                updateTotalAmount()
            }
            .addOnFailureListener { exception ->
                Log.e("MainActivity", "Failed to update item checked status: ${exception.message}")
                // Revert checkbox state on failure
                runOnUiThread {
                    if (position < items.size) {
                        adapter.notifyItemChanged(position)
                    }
                    Snackbar.make(
                        binding.root,
                        "Failed to update item: ${exception.message}",
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
            }
    }

    // **FIX: Complete deleteItem() method**
    private fun deleteItem(itemId: String) {
        Log.d("MainActivity", "Deleting item: $itemId")

        database.child(itemId).removeValue()
            .addOnSuccessListener {
                Log.d("MainActivity", "Item deleted successfully")
                Snackbar.make(
                    binding.root,
                    "Item deleted",
                    Snackbar.LENGTH_SHORT
                ).show()
            }
            .addOnFailureListener { exception ->
                Log.e("MainActivity", "Failed to delete item: ${exception.message}")
                Snackbar.make(
                    binding.root,
                    "Failed to delete item: ${exception.message}",
                    Snackbar.LENGTH_SHORT
                ).show()
            }
    }

    // **FIX: Complete updateTotalAmount() method**
    private fun updateTotalAmount() {
        var total = 0.0
        var checkedCount = 0

        for (item in items) {
            total += item.price * item.quantity
            if (item.isChecked) {
                checkedCount++
            }
        }

        runOnUiThread {
            // Update your total amount UI elements here
            // For example, if you have a TextView for total:
            // binding.textViewTotal.text = String.format("Total: $%.2f", total)
            // binding.textViewChecked.text = "Checked: $checkedCount/${items.size}"

            Log.d("MainActivity", "Total amount: $total, Checked items: $checkedCount/${items.size}")
        }
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
            val name = editTextItemName.text.toString().trim()
            val quantityStr = editTextQuantity.text.toString().trim()
            val priceStr = editTextPrice.text.toString().trim()

            if (name.isEmpty()) {
                editTextItemName.error = "Item name is required"
                return@setOnClickListener
            }

            val quantity = quantityStr.toIntOrNull() ?: 1
            val price = priceStr.toDoubleOrNull() ?: 0.0

            addItem(name, quantity, price)
            bottomSheetDialog.dismiss()
        }

        bottomSheetDialog.show()
    }

    // **FIX: Complete addItem() method**
    private fun addItem(name: String, quantity: Int, price: Double) {
        val itemId = database.push().key
        if (itemId == null) {
            Log.e("MainActivity", "Failed to generate item ID")
            Snackbar.make(binding.root, "Failed to add item", Snackbar.LENGTH_SHORT).show()
            return
        }

        val item = Items(
            id = itemId,
            name = name,
            quantity = quantity,
            price = price,
            isChecked = false
        )

        Log.d("MainActivity", "Adding item: $name")

        database.child(itemId).setValue(item)
            .addOnSuccessListener {
                Log.d("MainActivity", "Item added successfully")
                Snackbar.make(
                    binding.root,
                    "Item added: $name",
                    Snackbar.LENGTH_SHORT
                ).show()
            }
            .addOnFailureListener { exception ->
                Log.e("MainActivity", "Failed to add item: ${exception.message}")
                Snackbar.make(
                    binding.root,
                    "Failed to add item: ${exception.message}",
                    Snackbar.LENGTH_LONG
                ).show()
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up listener to prevent memory leaks
        valueEventListener?.let { listener ->
            database.removeEventListener(listener)
        }
    }
}
