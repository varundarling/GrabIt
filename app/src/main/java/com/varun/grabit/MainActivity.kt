package com.varun.grabit

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.varun.grabit.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity(), ItemAdapter.OnInteractionListener {

    private val items = mutableListOf<Items>()
    private lateinit var adapter: ItemAdapter
    private lateinit var binding: ActivityMainBinding
    private lateinit var mAdView: AdView

    // Local Storage
    private lateinit var sharedPrefs: SharedPreferences
    private val PREFS_LOCAL_NAME = "local_items_prefs"
    private val PREFS_LOCAL_KEY = "items_json"
    private val gson = Gson()

    // Selection mode
    private var isSelectionMode = false
    private val selectedItemIds = mutableSetOf<String>()

    // Ad Integration
    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null
    private var itemsAddedCount = 0
    private var lastAdTime = 0L

    companion object {
        private const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-7339218345159620/9019215941"
        private const val REWARDED_AD_UNIT_ID = "ca-app-pub-7339218345159620/7706134275"
        private const val AD_COOLDOWN_MINUTES = 4
        private const val ITEMS_THRESHOLD_FOR_AD = 3
        private const val PREFS_NAME = "GrabItAdPrefs"
        private const val KEY_LAST_AD_TIME = "last_ad_time"
        private const val KEY_ITEMS_ADDED_COUNT = "items_added_count"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPrefs = getSharedPreferences(PREFS_LOCAL_NAME, MODE_PRIVATE)

        initializeAdPreferences()
        setupUI()
        loadItems()
        setupAds()
    }

    private fun initializeAdPreferences() {
        val adPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        lastAdTime = adPrefs.getLong(KEY_LAST_AD_TIME, 0)
        itemsAddedCount = adPrefs.getInt(KEY_ITEMS_ADDED_COUNT, 0)
        Log.d("GrabIt", "Ad Stats - Items Added: $itemsAddedCount")
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "GrabIt Shopping"

        adapter = ItemAdapter(
            items = items,
            onItemClick = { item, _ -> handleItemClick(item) },
            onItemLongClick = { item, _ -> handleItemLongClick(item) },
            onDeleteClick = { item -> showDeleteConfirmation(item) },
            interactionListener = this
        )

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }

        binding.buttonAddItem.setOnClickListener {
            if (!isSelectionMode) {
                showAddItemDialog()
            }
        }

        setupSelectionBarButtons()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupAds() {
        Log.d("GrabIt", "Initializing AdMob")

        val requestConfiguration = RequestConfiguration.Builder()
            .setTagForChildDirectedTreatment(RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_FALSE)
            .build()
        MobileAds.setRequestConfiguration(requestConfiguration)

        MobileAds.initialize(this) { initStatus ->
            Log.d("GrabIt", "AdMob initialized: ${initStatus.adapterStatusMap}")
            loadBannerAd()
            loadInterstitialAd()
            loadRewardedAd()
        }
    }

    private fun loadBannerAd() {
        mAdView = binding.bannerAdView
        val adRequest = AdRequest.Builder().build()

        mAdView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                Log.d("GrabIt", "Banner ad loaded successfully")
            }
            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.w("GrabIt", "Banner ad failed: ${adError.message}")
            }
        }

        mAdView.loadAd(adRequest)
    }

    private fun loadInterstitialAd() {
        val adRequest = AdRequest.Builder().build()

        InterstitialAd.load(this, INTERSTITIAL_AD_UNIT_ID, adRequest, object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) {
                interstitialAd = ad
                Log.d("GrabIt", "Interstitial ad loaded")

                interstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        Log.d("GrabIt", "Interstitial ad dismissed")
                        interstitialAd = null
                        loadInterstitialAd()
                    }

                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        Log.e("GrabIt", "Interstitial ad failed to show: ${adError.message}")
                        interstitialAd = null
                        loadInterstitialAd()
                    }

                    override fun onAdShowedFullScreenContent() {
                        Log.d("GrabIt", "Interstitial ad shown")
                        updateLastAdTime()
                    }
                }
            }

            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.e("GrabIt", "Interstitial ad failed to load: ${adError.message}")
                interstitialAd = null
                Handler(Looper.getMainLooper()).postDelayed({
                    loadInterstitialAd()
                }, 30000)
            }
        })
    }

    private fun loadRewardedAd() {
        val adRequest = AdRequest.Builder().build()

        RewardedAd.load(this, REWARDED_AD_UNIT_ID, adRequest, object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) {
                rewardedAd = ad
                Log.d("GrabIt", "Rewarded ad loaded")

                rewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        Log.d("GrabIt", "Rewarded ad dismissed")
                        rewardedAd = null
                        loadRewardedAd()
                    }

                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        Log.e("GrabIt", "Rewarded ad failed to show: ${adError.message}")
                        rewardedAd = null
                        loadRewardedAd()
                    }

                    override fun onAdShowedFullScreenContent() {
                        Log.d("GrabIt", "Rewarded ad shown")
                        updateLastAdTime()
                    }
                }
            }

            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.e("GrabIt", "Rewarded ad failed to load: ${adError.message}")
                rewardedAd = null
                Handler(Looper.getMainLooper()).postDelayed({
                    loadRewardedAd()
                }, 30000)
            }
        })
    }

    private fun showInterstitialAd(reason: String) {
        if (interstitialAd != null) {
            Log.d("GrabIt", "Showing interstitial ad - Reason: $reason")
            interstitialAd?.show(this)
        } else {
            Log.w("GrabIt", "Interstitial ad not ready - Reason: $reason")
            loadInterstitialAd()
        }
    }

    private fun showRewardedAd(reason: String) {
        if (rewardedAd != null) {
            Log.d("GrabIt", "Showing rewarded ad - Reason: $reason")

            rewardedAd?.show(this) { rewardItem ->
                val rewardAmount = rewardItem.amount
                val rewardType = rewardItem.type
                Log.d("GrabIt", "User earned reward: $rewardAmount $rewardType")
                showSnackbar("Thanks for watching! You earned $rewardAmount $rewardType")
            }
        } else {
            Log.w("GrabIt", "Rewarded ad not ready - Reason: $reason")
            loadRewardedAd()
        }
    }

    private fun updateLastAdTime() {
        lastAdTime = System.currentTimeMillis()
        val adPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        adPrefs.edit().putLong(KEY_LAST_AD_TIME, lastAdTime).apply()
        Log.d("GrabIt", "Updated last ad time")
    }

    private fun incrementItemsAddedCount() {
        itemsAddedCount++
        val adPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        adPrefs.edit().putInt(KEY_ITEMS_ADDED_COUNT, itemsAddedCount).apply()

        Log.d("GrabIt", "Items added count: $itemsAddedCount")

        if (itemsAddedCount % ITEMS_THRESHOLD_FOR_AD == 0) {
            Log.d("GrabIt", "Reached $ITEMS_THRESHOLD_FOR_AD items threshold - Showing ad")

            val showRewardedAd = (0..1).random() == 1

            if (showRewardedAd && rewardedAd != null) {
                showRewardedAd("items_threshold")
            } else {
                showInterstitialAd("items_threshold")
            }
        }
    }

    private fun loadItems() {
        Log.d("GrabIt", "Loading items from SharedPreferences...")

        val json = sharedPrefs.getString(PREFS_LOCAL_KEY, null)
        if (!json.isNullOrEmpty()) {
            val type = object : TypeToken<MutableList<Items>>() {}.type
            val itemsList: MutableList<Items> = gson.fromJson(json, type)
            items.clear()
            items.addAll(itemsList)
        } else {
            items.clear()
        }
        adapter.notifyDataSetChanged()
        updateUI()
    }

    private fun saveItems() {
        val json = gson.toJson(items)
        sharedPrefs.edit().putString(PREFS_LOCAL_KEY, json).apply()
    }

    @SuppressLint("SetTextI18n", "DefaultLocale")
    private fun updateUI() {
        val isEmpty = items.isEmpty()
        val itemCount = items.size

        Log.d("GrabIt", "Updating UI - Items: $itemCount, Empty: $isEmpty")

        binding.recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
        binding.noItemsText.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.totalLayout.visibility = if (isEmpty) View.GONE else View.VISIBLE

        binding.itemCount.text = "$itemCount items"

        var totalAmount = 0.0
        items.forEach { item ->
            totalAmount += item.price * item.quantity
        }

        binding.totalAmount.text = "₹${String.format("%.2f", totalAmount)}"

        Log.d("GrabIt", "Updated - Count: $itemCount, Total: ₹$totalAmount")
    }

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
            Log.d("GrabIt", "Deselected item: $itemId")
        } else {
            selectedItemIds.add(itemId)
            Log.d("GrabIt", "Selected item: $itemId")
        }

        adapter.setItemSelected(itemId, selectedItemIds.contains(itemId))
        updateSelectionTitle()

        Log.d("GrabIt", "Current selection count: ${selectedItemIds.size}")

        if (selectedItemIds.isEmpty()) {
            Log.d("GrabIt", "Exiting selection mode - no items selected")
            exitSelectionMode()
        }
    }

    private fun startSelectionMode() {
        isSelectionMode = true
        selectedItemIds.clear()

        binding.selectionBar.visibility = View.VISIBLE
        adapter.setSelectionMode(true)

        setupSelectionBarButtons()

        Log.d("GrabIt", "Selection mode started")
    }

    private fun setupSelectionBarButtons() {
        binding.btnSelectAll.setOnClickListener {
            toggleSelectAll()
        }

        binding.btnDeleteSelected.setOnClickListener {
            deleteSelectedItems()
        }

        binding.btnCloseSelection.setOnClickListener {
            exitSelectionMode()
        }
    }

    private fun toggleSelectAll() {
        if (selectedItemIds.size == items.size) {
            // All items are selected, so deselect all
            deselectAllItems()
        } else {
            // Not all items are selected, so select all
            selectAllItems()
        }
    }

    private fun deselectAllItems() {
        selectedItemIds.clear()
        adapter.clearSelection()
        updateSelectionTitle()
    }

    private fun updateSelectionTitle() {
        val count = selectedItemIds.size

        val titleText = when (count) {
            0 -> "0 items selected"
            1 -> "1 item selected"
            else -> "$count items selected"
        }

        binding.selectionTitle.text = titleText

        // Update button text based on selection state
        if (count == items.size && items.isNotEmpty()) {
            binding.btnSelectAll.isEnabled = true
        } else if (items.isNotEmpty()) {
            binding.btnSelectAll.isEnabled = true
        } else {
            binding.btnSelectAll.isEnabled = false
        }

        binding.btnDeleteSelected.isEnabled = count > 0

        Log.d("GrabIt", "Selection bar updated: $titleText")

        if (count == 0 && isSelectionMode) {
            exitSelectionMode()
        }
    }

    private fun exitSelectionMode() {
        isSelectionMode = false
        selectedItemIds.clear()
        adapter.setSelectionMode(false)
        adapter.clearSelection()

        binding.selectionBar.visibility = View.GONE

        Log.d("GrabIt", "Selection mode exited")
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
                items.removeAll { selectedItemIds.contains(it.id) }
                saveItems()
                adapter.notifyDataSetChanged()
                exitSelectionMode()
                showSnackbar("✅ $count items deleted")
                updateUI()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onSelectionChanged(selectedCount: Int) {
        updateSelectionTitle()
    }

    private fun updateItemStatus(itemId: String, isChecked: Boolean) {
        val item = items.find { it.id == itemId }
        item?.isChecked = isChecked
        saveItems()
        val pos = items.indexOf(item)
        if (pos != -1) adapter.notifyItemChanged(pos)
        updateUI()
    }

    private fun showDeleteConfirmation(item: Items) {
        AlertDialog.Builder(this)
            .setTitle("Delete Item")
            .setMessage("Remove \"${item.name}\"?")
            .setPositiveButton("Delete") { _, _ ->
                Log.d("GrabIt", "Deleting: ${item.name}")
                deleteItem(item)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteItem(item: Items) {
        val position = items.indexOfFirst { it.id == item.id }
        if (position == -1) return

        items.removeAt(position)
        saveItems()
        adapter.notifyItemRemoved(position)
        updateUI()

        if (isSelectionMode) {
            selectedItemIds.remove(item.id)
            if (selectedItemIds.isEmpty()) exitSelectionMode()
        }
        showSnackbar("✅ ${item.name} removed")
    }

    @SuppressLint("InflateParams")
    private fun showAddItemDialog() {
        Log.d("GrabIt", "Opening add item dialog")

        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_add_item, null)
        dialog.setContentView(view)

        val nameInput = view.findViewById<EditText>(R.id.editItemName)
        val quantityInput = view.findViewById<EditText>(R.id.editQuantity)
        val priceInput = view.findViewById<EditText>(R.id.editPrice)
        val addButton = view.findViewById<Button>(R.id.buttonAdd)

        quantityInput.setText("1")

        // Add Enter key support
        val enterKeyListener = { _: android.view.View, actionId: Int, event: android.view.KeyEvent? ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                (event?.keyCode == android.view.KeyEvent.KEYCODE_ENTER && event.action == android.view.KeyEvent.ACTION_DOWN)) {
                addButton.performClick()
                true
            } else {
                false
            }
        }

        nameInput.setOnEditorActionListener(enterKeyListener)
        quantityInput.setOnEditorActionListener(enterKeyListener)
        priceInput.setOnEditorActionListener(enterKeyListener)

        addButton.setOnClickListener {
            Log.d("GrabIt", "Add button clicked")

            val name = nameInput.text.toString().trim()
            val quantityStr = quantityInput.text.toString().trim()
            val priceStr = priceInput.text.toString().trim()

            Log.d("GrabIt", "Form data: name='$name', qty='$quantityStr', price='$priceStr'")

            when {
                name.isEmpty() -> {
                    nameInput.error = "Enter item name"
                    Log.w("GrabIt", "Empty name")
                    return@setOnClickListener
                }
                quantityStr.isEmpty() -> {
                    quantityInput.error = "Enter quantity"
                    Log.w("GrabIt", "Empty quantity")
                    return@setOnClickListener
                }
                priceStr.isEmpty() -> {
                    priceInput.error = "Enter price"
                    Log.w("GrabIt", "Empty price")
                    return@setOnClickListener
                }
            }

            val quantity = quantityStr.toIntOrNull()
            val price = priceStr.toDoubleOrNull()

            when {
                quantity == null || quantity <= 0 -> {
                    quantityInput.error = "Enter valid quantity"
                    Log.w("GrabIt", "Invalid quantity: $quantity")
                    return@setOnClickListener
                }
                price == null || price <= 0 -> {
                    priceInput.error = "Enter valid price"
                    Log.w("GrabIt", "Invalid price: $price")
                    return@setOnClickListener
                }
                else -> {
                    Log.d("GrabIt", "Validation passed - adding item")
                    addItem(name, quantity, price)
                    dialog.dismiss()
                }
            }
        }

        dialog.show()
    }

    private fun addItem(name: String, quantity: Int, price: Double) {
        val id = System.currentTimeMillis().toString()
        val item = Items(id = id, name = name, quantity = quantity, price = price, isChecked = false)
        items.add(item)
        saveItems()
        adapter.notifyItemInserted(items.size - 1)
        updateUI()
        showSnackbar("✅ $name added")
        incrementItemsAddedCount()
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
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
                    .setMessage("GrabIt Shopping List v2.1.0\n\n" +
                            "⭐ If you love GrabIt, please rate us 5 stars on Google Play Store!" +
                            "\n\nMade with ❤️ by Varun Darling")
                    .setPositiveButton("OK", null)
                    .show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (isSelectionMode) {
            exitSelectionMode()
        } else {
            super.onBackPressed()
        }
    }
}
