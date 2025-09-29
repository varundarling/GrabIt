package com.varun.grabit

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.varun.grabit.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity(), ItemAdapter.OnItemInteractionListener {

    private val items = mutableListOf<Items>()
    private lateinit var adapter: ItemAdapter
    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private var valueEventListener: ValueEventListener? = null
    private lateinit var binding: ActivityMainBinding
    private lateinit var mAdView: AdView

    // Selection mode
    private var actionMode: ActionMode? = null
    private var isSelectionMode = false
    private val selectedItemIds = mutableSetOf<String>()

    // AD INTEGRATION - Variables
    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null
    private lateinit var sharedPrefs: SharedPreferences
    private var itemsAddedCount = 0
    private var lastAdTime = 0L
    private var lastAppOpenTime = 0L

    // Ad Unit IDs - Replace with your actual Ad Unit IDs
    companion object {
        private const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-7339218345159620/9019215941"
        private const val REWARDED_AD_UNIT_ID = "ca-app-pub-7339218345159620/7706134275"
        private const val AD_COOLDOWN_MINUTES = 4
        private const val ITEMS_THRESHOLD_FOR_AD = 3
        private const val PREFS_NAME = "GrabItAdPrefs"
        private const val KEY_LAST_AD_TIME = "last_ad_time"
        private const val KEY_ITEMS_ADDED_COUNT = "items_added_count"
        private const val KEY_LAST_APP_OPEN = "last_app_open"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Log.d("GrabIt", "🚀 GrabIt Starting with Advanced Ads")

        initializeAdPreferences()
        checkAndShowAppOpenAd()
        setupUI()
        setupFirebase()
        setupAds()
    }

    // AD INTEGRATION - Initialize Preferences
    private fun initializeAdPreferences() {
        sharedPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        lastAdTime = sharedPrefs.getLong(KEY_LAST_AD_TIME, 0)
        itemsAddedCount = sharedPrefs.getInt(KEY_ITEMS_ADDED_COUNT, 0)
        lastAppOpenTime = sharedPrefs.getLong(KEY_LAST_APP_OPEN, 0)

        Log.d("GrabIt", "📊 Ad Stats - Items Added: $itemsAddedCount, Last Ad: ${(System.currentTimeMillis() - lastAdTime) / 60000} min ago")
    }

    // AD INTEGRATION - Check App Open Ad (Every 5 minutes)
    private fun checkAndShowAppOpenAd() {
        val currentTime = System.currentTimeMillis()
        val timeDifference = currentTime - lastAppOpenTime
        val cooldownTime = AD_COOLDOWN_MINUTES * 60 * 1000L // 5 minutes

        // Save current app open time
        sharedPrefs.edit().putLong(KEY_LAST_APP_OPEN, currentTime).apply()

        if (timeDifference > cooldownTime) {
            Log.d("GrabIt", "⏰ App opened after ${timeDifference / 60000} minutes - Preparing interstitial")

            // Show interstitial ad after 3 seconds delay
            Handler(Looper.getMainLooper()).postDelayed({
                showInterstitialAd("app_open")
            }, 3000)
        } else {
            val remainingTime = (cooldownTime - timeDifference) / 60000
            Log.d("GrabIt", "⏰ Too early for app open ad - $remainingTime minutes remaining")
        }
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "GrabIt Shopping"

        // Setup RecyclerView
        adapter = ItemAdapter(
            items = items,
            onItemClick = { item, position -> handleItemClick(item) },
            onItemLongClick = { item, position -> handleItemLongClick(item) },
            onDeleteClick = { item -> showDeleteConfirmation(item) },
            interactionListener = this
        )

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }

        binding.fabAddItem.setOnClickListener {
            if (!isSelectionMode) {
                showAddItemDialog()
            }
        }

        // Setup custom selection bar buttons
        setupSelectionBarButtons()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    // AD INTEGRATION - Complete Ad Setup
    private fun setupAds() {
        Log.d("GrabIt", "🎯 Initializing AdMob with Rewarded & Interstitial Ads")

        val requestConfiguration = RequestConfiguration.Builder()
            .setTagForChildDirectedTreatment(RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_FALSE)
            .build()
        MobileAds.setRequestConfiguration(requestConfiguration)

        MobileAds.initialize(this) { initStatus ->
            Log.d("GrabIt", "✅ AdMob initialized: ${initStatus.adapterStatusMap}")

            // Load all ad types after initialization
            loadBannerAd()
            loadInterstitialAd()
            loadRewardedAd()
        }
    }

    // AD INTEGRATION - Banner Ad
    private fun loadBannerAd() {
        mAdView = binding.bannerAdView
        val adRequest = AdRequest.Builder().build()

        mAdView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                Log.d("GrabIt", "✅ Banner ad loaded successfully")
            }
            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.w("GrabIt", "❌ Banner ad failed: ${adError.message}")
            }
            override fun onAdClicked() {
                Log.d("GrabIt", "👆 Banner ad clicked")
            }
        }

        mAdView.loadAd(adRequest)
    }

    // AD INTEGRATION - Interstitial Ad
    private fun loadInterstitialAd() {
        val adRequest = AdRequest.Builder().build()

        InterstitialAd.load(this, INTERSTITIAL_AD_UNIT_ID, adRequest, object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) {
                interstitialAd = ad
                Log.d("GrabIt", "✅ Interstitial ad loaded")

                interstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        Log.d("GrabIt", "📱 Interstitial ad dismissed")
                        interstitialAd = null
                        // Reload for next time
                        loadInterstitialAd()
                    }

                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        Log.e("GrabIt", "❌ Interstitial ad failed to show: ${adError.message}")
                        interstitialAd = null
                        loadInterstitialAd()
                    }

                    override fun onAdShowedFullScreenContent() {
                        Log.d("GrabIt", "📺 Interstitial ad shown")
                        updateLastAdTime()
                    }
                }
            }

            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.e("GrabIt", "❌ Interstitial ad failed to load: ${adError.message}")
                interstitialAd = null

                // Retry loading after 30 seconds
                Handler(Looper.getMainLooper()).postDelayed({
                    loadInterstitialAd()
                }, 30000)
            }
        })
    }

    // AD INTEGRATION - Rewarded Ad
    private fun loadRewardedAd() {
        val adRequest = AdRequest.Builder().build()

        RewardedAd.load(this, REWARDED_AD_UNIT_ID, adRequest, object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) {
                rewardedAd = ad
                Log.d("GrabIt", "✅ Rewarded ad loaded")

                rewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        Log.d("GrabIt", "📱 Rewarded ad dismissed")
                        rewardedAd = null
                        // Reload for next time
                        loadRewardedAd()
                    }

                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        Log.e("GrabIt", "❌ Rewarded ad failed to show: ${adError.message}")
                        rewardedAd = null
                        loadRewardedAd()
                    }

                    override fun onAdShowedFullScreenContent() {
                        Log.d("GrabIt", "📺 Rewarded ad shown")
                        updateLastAdTime()
                    }
                }
            }

            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.e("GrabIt", "❌ Rewarded ad failed to load: ${adError.message}")
                rewardedAd = null

                // Retry loading after 30 seconds
                Handler(Looper.getMainLooper()).postDelayed({
                    loadRewardedAd()
                }, 30000)
            }
        })
    }

    // AD INTEGRATION - Show Interstitial Ad
    private fun showInterstitialAd(reason: String) {
        if (interstitialAd != null) {
            Log.d("GrabIt", "📺 Showing interstitial ad - Reason: $reason")
            interstitialAd?.show(this)
        } else {
            Log.w("GrabIt", "⚠️ Interstitial ad not ready - Reason: $reason")
            // Try to load a new one
            loadInterstitialAd()
        }
    }

    // AD INTEGRATION - Show Rewarded Ad
    private fun showRewardedAd(reason: String) {
        if (rewardedAd != null) {
            Log.d("GrabIt", "🎁 Showing rewarded ad - Reason: $reason")

            rewardedAd?.show(this) { rewardItem ->
                val rewardAmount = rewardItem.amount
                val rewardType = rewardItem.type
                Log.d("GrabIt", "🏆 User earned reward: $rewardAmount $rewardType")

                // Give user reward (you can implement bonus features here)
                showSnackbar("🎉 Thanks for watching! You earned $rewardAmount $rewardType")
            }
        } else {
            Log.w("GrabIt", "⚠️ Rewarded ad not ready - Reason: $reason")
            // Try to load a new one
            loadRewardedAd()
        }
    }

    // AD INTEGRATION - Update Last Ad Time
    private fun updateLastAdTime() {
        lastAdTime = System.currentTimeMillis()
        sharedPrefs.edit().putLong(KEY_LAST_AD_TIME, lastAdTime).apply()
        Log.d("GrabIt", "⏰ Updated last ad time")
    }

    // AD INTEGRATION - Update Items Added Count
    private fun incrementItemsAddedCount() {
        itemsAddedCount++
        sharedPrefs.edit().putInt(KEY_ITEMS_ADDED_COUNT, itemsAddedCount).apply()

        Log.d("GrabIt", "📊 Items added count: $itemsAddedCount")

        // Show ad every 6 items
        if (itemsAddedCount % ITEMS_THRESHOLD_FOR_AD == 0) {
            Log.d("GrabIt", "🎯 Reached $ITEMS_THRESHOLD_FOR_AD items threshold - Showing ad")

            // Randomly show either interstitial or rewarded ad
            val showRewardedAd = (0..1).random() == 1

            if (showRewardedAd && rewardedAd != null) {
                showRewardedAd("items_threshold")
            } else {
                showInterstitialAd("items_threshold")
            }
        }
    }

    // FIXED - Simplified Firebase Setup
    private fun setupFirebase() {
        Log.d("GrabIt", "🔥 Setting up Firebase...")

        // Enable persistence first
        try {
            FirebaseDatabase.getInstance().setPersistenceEnabled(true)
            Log.d("GrabIt", "✅ Persistence enabled")
        } catch (e: Exception) {
            Log.d("GrabIt", "Persistence already enabled")
        }

        auth = FirebaseAuth.getInstance()

        // Simple authentication - back to original working method
        authenticateAndConnect()
    }

    private fun authenticateAndConnect() {
        binding.progressBar.visibility = View.VISIBLE

        Log.d("GrabIt", "🔐 Authenticating...")

        auth.signInAnonymously().addOnCompleteListener { task ->
            binding.progressBar.visibility = View.GONE

            if (task.isSuccessful) {
                val user = auth.currentUser
                val userId = user?.uid ?: "default"

                Log.d("GrabIt", "✅ Authentication SUCCESS")
                Log.d("GrabIt", "👤 User ID: $userId")

                database = FirebaseDatabase.getInstance().getReference("items").child(userId)
                database.keepSynced(true)

                Log.d("GrabIt", "🗄️ Database path: items/$userId")

                loadItems()

            } else {
                Log.e("GrabIt", "❌ Authentication FAILED", task.exception)
                showRetryDialog("Connection Failed",
                    "Cannot connect to Firebase. Please check your internet and try again.") {
                    authenticateAndConnect()
                }
            }
        }
    }

    private fun loadItems() {
        Log.d("GrabIt", "📥 Loading items from Firebase...")

        binding.progressBar?.visibility = View.VISIBLE

        // Remove existing listener
        valueEventListener?.let { database.removeEventListener(it) }

        valueEventListener = object : ValueEventListener {
            @SuppressLint("NotifyDataSetChanged")
            override fun onDataChange(snapshot: DataSnapshot) {
                val itemCount = snapshot.childrenCount.toInt()
                Log.d("GrabIt", "📊 Firebase data changed: $itemCount items found")

                val itemsList = mutableListOf<Items>()

                for (itemSnapshot in snapshot.children) {
                    try {
                        val item = itemSnapshot.getValue(Items::class.java)
                        if (item != null) {
                            itemsList.add(item)
                            Log.d("GrabIt", "📦 Loaded: ${item.name}")
                        }
                    } catch (e: Exception) {
                        Log.e("GrabIt", "❌ Failed to parse item: ${itemSnapshot.key}", e)
                    }
                }

                // Sort items
                itemsList.sortWith(compareBy<Items> { it.isChecked }.thenBy { it.name })

                runOnUiThread {
                    binding.progressBar.visibility = View.GONE

                    Log.d("GrabIt", "🔄 Updating UI with ${itemsList.size} items")

                    items.clear()
                    items.addAll(itemsList)
                    adapter.notifyDataSetChanged()
                    updateUI()

                    Log.d("GrabIt", "✅ UI update complete: ${items.size} items")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("GrabIt", "❌ Database ERROR: ${error.message}")

                runOnUiThread {
                    binding.progressBar.visibility = View.GONE
                    showSnackbar("❌ Database error: ${error.message}")

                    if (error.code == DatabaseError.PERMISSION_DENIED) {
                        showRetryDialog("Permission Denied",
                            "Cannot access database. Please check Firebase rules.") {
                            authenticateAndConnect()
                        }
                    }
                }
            }
        }

        database.addValueEventListener(valueEventListener!!)
        Log.d("GrabIt", "👂 Firebase listener attached")
    }

    @SuppressLint("SetTextI18n", "DefaultLocale")
    private fun updateUI() {
        val isEmpty = items.isEmpty()
        val itemCount = items.size

        Log.d("GrabIt", "🎨 Updating UI - Items: $itemCount, Empty: $isEmpty")

        // Update visibility
        binding.recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
        binding.noItemsText.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.totalLayout.visibility = if (isEmpty) View.GONE else View.VISIBLE

        // Update item count
        binding.itemCount.text = "$itemCount items"

        // Calculate and update total
        var totalAmount = 0.0
        items.forEach { item ->
            totalAmount += item.price * item.quantity
        }

        binding.totalAmount.text = "₹${String.format("%.2f", totalAmount)}"

        Log.d("GrabIt", "💰 Updated - Count: $itemCount, Total: ₹$totalAmount")
    }

    // Item Operations
    private fun handleItemClick(item: Items) {
        if (isSelectionMode) {
            toggleItemSelection(item.id)
        } else {
            val newStatus = !item.isChecked
            updateItemStatus(item.id, newStatus)
            showSnackbar(if (newStatus) "✅ ${item.name} completed!" else "↩️ ${item.name} pending")
        }
    }

    private fun handleItemLongClick(item: Items): Boolean {
        if (!isSelectionMode) {
            startSelectionMode()
            toggleItemSelection(item.id)
            return true
        }
        return false
    }

    private fun toggleItemSelection(itemId: String) {
        val wasSelected = selectedItemIds.contains(itemId)

        if (wasSelected) {
            selectedItemIds.remove(itemId)
            Log.d("GrabIt", "❌ Deselected item: $itemId")
        } else {
            selectedItemIds.add(itemId)
            Log.d("GrabIt", "✅ Selected item: $itemId")
        }

        // Update adapter selection state
        adapter.setItemSelected(itemId, selectedItemIds.contains(itemId))

        // Update toolbar title
        updateSelectionTitle()

        // FIXED: Exit selection mode when count reaches zero
        Log.d("GrabIt", "📊 Current selection count: ${selectedItemIds.size}")

        if (selectedItemIds.isEmpty()) {
            Log.d("GrabIt", "🚪 Exiting selection mode - no items selected")
            exitSelectionMode()
        }
    }

    private fun startSelectionMode() {
        isSelectionMode = true
        selectedItemIds.clear()

        // Show custom selection bar
        binding.selectionBar.visibility = View.VISIBLE
        adapter.setSelectionMode(true)

        // Setup custom selection bar buttons
        setupSelectionBarButtons()

        Log.d("GrabIt", "📱 Custom selection bar shown below ad")
    }

    private fun setupSelectionBarButtons() {
        // Select All button
        binding.btnSelectAll.setOnClickListener {
            selectAllItems()
        }

        // Delete Selected button
        binding.btnDeleteSelected.setOnClickListener {
            deleteSelectedItems()
        }

        // Close Selection button
        binding.btnCloseSelection.setOnClickListener {
            exitSelectionMode()
        }
    }

    private fun updateSelectionTitle() {
        val count = selectedItemIds.size

        val titleText = when (count) {
            0 -> "0 items selected"
            1 -> "1 item selected"
            else -> "$count items selected"
        }

        binding.selectionTitle.text = titleText

        // Enable/disable buttons based on selection
        binding.btnSelectAll.isEnabled = count < items.size
        binding.btnDeleteSelected.isEnabled = count > 0

        Log.d("GrabIt", "📋 Selection bar updated: $titleText")

        // Exit selection mode when count reaches zero
        if (count == 0 && isSelectionMode) {
            exitSelectionMode()
        }
    }

    private fun exitSelectionMode() {
        isSelectionMode = false
        selectedItemIds.clear()
        adapter.setSelectionMode(false)
        adapter.clearSelection()

        // Hide custom selection bar
        binding.selectionBar.visibility = View.GONE

        Log.d("GrabIt", "📱 Custom selection bar hidden")
    }

    private fun selectAllItems() {
        selectedItemIds.clear()
        items.forEach { selectedItemIds.add(it.id) }
        adapter.selectAll()
        updateSelectionTitle()
    }

    private fun deleteSelectedItems() {
        if (selectedItemIds.isEmpty()) return

        val count = selectedItemIds.size
        AlertDialog.Builder(this)
            .setTitle("Delete Items")
            .setMessage("Delete $count item${if (count > 1) "s" else ""}?")
            .setPositiveButton("Delete") { _, _ ->
                selectedItemIds.forEach { itemId ->
                    database.child(itemId).removeValue()
                }
                exitSelectionMode()
                showSnackbar("✅ $count items deleted")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onSelectionChanged(selectedCount: Int) {
        updateSelectionTitle()
    }

    private fun updateItemStatus(itemId: String, isChecked: Boolean) {
        Log.d("GrabIt", "🔄 Updating status: $itemId = $isChecked")

        database.child(itemId).child("isChecked").setValue(isChecked)
            .addOnSuccessListener {
                Log.d("GrabIt", "✅ Status updated")
            }
            .addOnFailureListener { exception ->
                Log.e("GrabIt", "❌ Status update failed", exception)
                showSnackbar("❌ Update failed")
            }
    }

    private fun showDeleteConfirmation(item: Items) {
        AlertDialog.Builder(this)
            .setTitle("Delete Item")
            .setMessage("Remove \"${item.name}\"?")
            .setPositiveButton("Delete") { _, _ ->
                Log.d("GrabIt", "🗑️ Deleting: ${item.name}")

                database.child(item.id).removeValue()
                    .addOnSuccessListener {
                        Log.d("GrabIt", "✅ Item deleted")
                        showSnackbar("✅ ${item.name} removed")
                    }
                    .addOnFailureListener { exception ->
                        Log.e("GrabIt", "❌ Delete failed", exception)
                        showSnackbar("❌ Delete failed")
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    @SuppressLint("InflateParams")
    private fun showAddItemDialog() {
        Log.d("GrabIt", "📝 Opening add item dialog")

        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_add_item, null)
        dialog.setContentView(view)

        val nameInput = view.findViewById<EditText>(R.id.editItemName)
        val quantityInput = view.findViewById<EditText>(R.id.editQuantity)
        val priceInput = view.findViewById<EditText>(R.id.editPrice)
        val addButton = view.findViewById<Button>(R.id.buttonAdd)

        quantityInput.setText("1")

        addButton.setOnClickListener {
            Log.d("GrabIt", "➕ Add button clicked")

            val name = nameInput.text.toString().trim()
            val quantityStr = quantityInput.text.toString().trim()
            val priceStr = priceInput.text.toString().trim()

            Log.d("GrabIt", "📝 Form data: name='$name', qty='$quantityStr', price='$priceStr'")

            when {
                name.isEmpty() -> {
                    nameInput.error = "Enter item name"
                    Log.w("GrabIt", "❌ Empty name")
                    return@setOnClickListener
                }
                quantityStr.isEmpty() -> {
                    quantityInput.error = "Enter quantity"
                    Log.w("GrabIt", "❌ Empty quantity")
                    return@setOnClickListener
                }
                priceStr.isEmpty() -> {
                    priceInput.error = "Enter price"
                    Log.w("GrabIt", "❌ Empty price")
                    return@setOnClickListener
                }
            }

            val quantity = quantityStr.toIntOrNull()
            val price = priceStr.toDoubleOrNull()

            when {
                quantity == null || quantity <= 0 -> {
                    quantityInput.error = "Enter valid quantity"
                    Log.w("GrabIt", "❌ Invalid quantity: $quantity")
                    return@setOnClickListener
                }
                price == null || price <= 0 -> {
                    priceInput.error = "Enter valid price"
                    Log.w("GrabIt", "❌ Invalid price: $price")
                    return@setOnClickListener
                }
                else -> {
                    Log.d("GrabIt", "✅ Validation passed - adding item")
                    addItem(name, quantity, price)
                    dialog.dismiss()
                }
            }
        }

        dialog.show()
    }

    // AD INTEGRATION - Modified addItem method with ad trigger
    private fun addItem(name: String, quantity: Int, price: Double) {
        Log.d("GrabIt", "➕ Adding item to database...")
        Log.d("GrabIt", "📦 Item details: $name x$quantity @ ₹$price")

        val itemId = database.push().key
        if (itemId == null) {
            Log.e("GrabIt", "❌ Failed to generate item ID")
            showSnackbar("❌ Failed to generate ID")
            return
        }

        Log.d("GrabIt", "🆔 Generated ID: $itemId")

        val item = Items(
            id = itemId,
            name = name,
            quantity = quantity,
            price = price,
            isChecked = false
        )

        Log.d("GrabIt", "💾 Writing to Firebase: items/${auth.currentUser?.uid}/$itemId")

        database.child(itemId).setValue(item)
            .addOnSuccessListener {
                Log.d("GrabIt", "✅ Item added to Firebase successfully!")
                showSnackbar("✅ $name added")

                // AD INTEGRATION - Increment count and check for ad display
                incrementItemsAddedCount()
            }
            .addOnFailureListener { exception ->
                Log.e("GrabIt", "❌ Failed to add item to Firebase", exception)
                showSnackbar("❌ Failed to add item: ${exception.message}")
            }
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    private fun showRetryDialog(title: String, message: String, onRetry: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Retry") { _, _ -> onRetry() }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (!isSelectionMode) {
            menuInflater.inflate(R.menu.main_menu, menu)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_about -> {
                AlertDialog.Builder(this)
                    .setTitle("About GrabIt")
                    .setMessage("GrabIt Shopping List v2.0.0\n\n" +
                            "⭐ If you love GrabIt, please rate us 5 stars on Google Play Store!" +
                            "\n\nMade with ❤️ by Varun Darling")
                    .setPositiveButton("OK", null)
                    .show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    @Deprecated("This method has been deprecated in favor of using the\n      {@link OnBackPressedDispatcher} via {@link #getOnBackPressedDispatcher()}.\n      The OnBackPressedDispatcher controls how back button events are dispatched\n      to one or more {@link OnBackPressedCallback} objects.")
    override fun onBackPressed() {
        if (isSelectionMode) {
            exitSelectionMode()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        valueEventListener?.let { database.removeEventListener(it) }
        Log.d("GrabIt", "🔄 App destroyed")
    }
}
