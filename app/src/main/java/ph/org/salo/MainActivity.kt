package ph.org.salo

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.*
import org.json.JSONObject
import org.json.JSONArray
import java.util.Locale

/** Native Android UI, with local storage and local deterministic decision engine. */
class MainActivity : Activity() {
    private val pine = Color.rgb(22, 108, 76)
    private val navy = Color.rgb(27, 49, 41)
    private val muted = Color.rgb(94, 115, 106)
    private val canvas = Color.rgb(245, 248, 246)
    private val white = Color.WHITE
    private val edge = Color.rgb(222, 233, 225)
    private val locs = listOf("Bulalacao", "Naujan", "Baco", "Puerto Galera")
    private val prefs by lazy { getSharedPreferences("salo_native_v1", MODE_PRIVATE) }
    private lateinit var input: ClimateInput
    private var tab = 0
    private lateinit var content: LinearLayout
    private lateinit var bottomBar: LinearLayout
    private var spokenInput: EditText? = null
    private var speech: SpeechRecognizer? = null
    private var pendingMicrophone = false
    private var quickFirst: EditText? = null
    private var quickSecond: EditText? = null
    private var quickDrainage: Spinner? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = canvas
        window.navigationBarColor = white
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        load()
        render()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun background(color: Int = white, radius: Int = 18, stroke: Int = edge): GradientDrawable = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat(); if (stroke != Color.TRANSPARENT) setStroke(dp(1), stroke)
    }
    private fun stack(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun label(t: String, size: Float = 15f, color: Int = navy, bold: Boolean = false): TextView = TextView(this).apply {
        text = t; textSize = size; setTextColor(color); if (bold) setTypeface(null, Typeface.BOLD)
        includeFontPadding = true
    }
    private fun block(t: String, size: Float = 15f, color: Int = navy, bold: Boolean = false): TextView = label(t, size, color, bold).apply {
        setPadding(0, dp(3), 0, dp(3))
    }
    private fun space(h: Int = 12) = Space(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(h)) }
    private fun button(text: String, primary: Boolean = true, action: () -> Unit) = TextView(this).apply {
        this.text = text
        textSize = 15f; setTypeface(null, Typeface.BOLD)
        gravity = Gravity.CENTER; minimumHeight = dp(52)
        setTextColor(if (primary) white else pine)
        background = background(if (primary) pine else white, 15, if (primary) pine else edge)
        setPadding(dp(14), dp(9), dp(14), dp(9))
        isClickable = true; isFocusable = true
        setOnClickListener { action() }
        contentDescription = text
    }
    private fun card(inside: LinearLayout.() -> Unit): LinearLayout = stack().apply {
        background = background()
        setPadding(dp(16), dp(17), dp(16), dp(17))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
        inside()
    }
    private fun textInput(value: String, hint: String, numeric: Boolean = false): EditText = EditText(this).apply {
        setSingleLine(!hint.startsWith("Tell")); textSize = 16f
        setTextColor(navy); setHintTextColor(muted); setText(value); setSelection(text.length)
        this.hint = hint; background = background(canvas, 12, edge)
        setPadding(dp(14), dp(12), dp(14), dp(12))
        if (numeric) inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        else inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        layoutParams = LinearLayout.LayoutParams(-1, dp(52))
    }
    private fun field(group: LinearLayout, title: String, value: String, hint: String): EditText {
        group.addView(block(title, 13f, muted, true)); val e = textInput(value, hint, true); group.addView(e); group.addView(space(12)); return e
    }
    private fun row(vararg views: View): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        views.forEachIndexed { i, v -> addView(v, LinearLayout.LayoutParams(0, -2, 1f).apply { if (i > 0) leftMargin = dp(10) }) }
    }
    private fun closeKeyboard() { (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.hideSoftInputFromWindow(window.decorView.windowToken, 0) }

    private fun stored(key: String): JSONObject? = try { JSONObject(prefs.getString(key, "") ?: "") } catch (_: Exception) { null }
    private fun defaultFor(place: String): ClimateInput = when(place) {
        "Naujan" -> ClimateInput(location=place, storedLiters=15500.0, dailyLiters=2100.0, rainMm=120.0, drainage=Drainage.CLEAR, tankCapacityLiters=22000.0, roofAreaSqM=225.0)
        "Baco" -> ClimateInput(location=place, storedLiters=8500.0, dailyLiters=2700.0, rainMm=160.0, drainage=Drainage.BLOCKED, tankCapacityLiters=14000.0, roofAreaSqM=210.0)
        "Puerto Galera" -> ClimateInput(location=place, storedLiters=18000.0, dailyLiters=2300.0, rainMm=135.0, drainage=Drainage.REVIEW, tankCapacityLiters=25000.0, roofAreaSqM=240.0)
        else -> ClimateInput()
    }
    private fun load() {
        val loc = prefs.getString("active_location", "Bulalacao")?.takeIf { it in locs } ?: "Bulalacao"
        val mode = if (prefs.getString("active_mode", "DRY") == "WET") ClimateMode.WET else ClimateMode.DRY
        input = readLocation(loc).copy(mode=mode)
    }
    private fun readLocation(loc: String): ClimateInput {
        val d = defaultFor(loc); val j = stored("place_$loc") ?: return d
        return d.copy(storedLiters=j.optDouble("stored",d.storedLiters), dailyLiters=j.optDouble("demand",d.dailyLiters), rainMm=j.optDouble("rain",d.rainMm),
            drainage=runCatching { Drainage.valueOf(j.optString("drain", d.drainage.name)) }.getOrDefault(d.drainage),
            tankCapacityLiters=j.optDouble("capacity",d.tankCapacityLiters),roofAreaSqM=j.optDouble("roof",d.roofAreaSqM))
    }
    private fun save() {
        val j = JSONObject().put("stored",input.storedLiters).put("demand", input.dailyLiters)
            .put("rain",input.rainMm).put("drain",input.drainage.name).put("capacity",input.tankCapacityLiters).put("roof",input.roofAreaSqM)
        prefs.edit().putString("active_location",input.location).putString("active_mode",input.mode.name).putString("place_${input.location}", j.toString()).apply()
    }
    private fun planKey() = "plan_${input.location}_${input.mode.name}"
    private fun signature(): String = listOf(input.location, input.mode.name, input.storedLiters, input.dailyLiters, input.rainMm, input.drainage, input.tankCapacityLiters, input.roofAreaSqM).joinToString("|")
    private fun hasCurrentPlan() = prefs.getString(planKey(), "") == signature()
    private fun finishedTasks(): MutableSet<String> {
        val arr = try { JSONArray(prefs.getString("done_${planKey()}", "[]")) } catch (_:Exception) { JSONArray() }
        return (0 until arr.length()).map { arr.optString(it) }.toMutableSet()
    }
    private fun persistDone(ids: Set<String>) {
        val a = JSONArray(); ids.forEach { a.put(it) }; prefs.edit().putString("done_${planKey()}", a.toString()).apply()
    }
    private fun createPlan(readQuick: Boolean = false) {
        if (readQuick && !applyQuick()) return
        if (!input.valid()) { msg("Check the entered water values before generating a plan."); return }
        val same = hasCurrentPlan()
        prefs.edit().putString(planKey(),signature()).apply()
        if (!same) persistDone(emptySet())
        tab=1; closeKeyboard(); render()
    }
    private fun applyQuick(): Boolean {
        val value = quickFirst?.text?.toString()?.trim()?.toDoubleOrNull()
        if (value == null) { msg("Enter a valid number for the first field."); return false }
        if (input.mode == ClimateMode.DRY) {
            val demand = quickSecond?.text?.toString()?.trim()?.toDoubleOrNull()
            if (demand == null || value < 0 || value > 100_000_000 || demand <= 0 || demand > 100_000_000) {
                msg("Enter a valid water reserve and daily demand."); return false
            }
            input = input.copy(storedLiters=value, dailyLiters=demand, tankCapacityLiters=maxOf(input.tankCapacityLiters,value))
        } else {
            if (value !in 0.0..10000.0) { msg("Enter a rainfall scenario between 0 and 10,000 mm."); return false }
            val d = when(quickDrainage?.selectedItemPosition){1->Drainage.CLEAR;2->Drainage.BLOCKED;else->Drainage.REVIEW}
            input=input.copy(rainMm=value, drainage=d)
        }
        save()
        return true
    }
    private fun changeMode(mode: ClimateMode) { input=input.copy(mode=mode); save(); render() }
    private fun changeLocation(loc: String) { if (loc == input.location) return; save(); input=readLocation(loc).copy(mode=input.mode); save(); render() }
    private fun msg(s: String) { Toast.makeText(this, s, Toast.LENGTH_LONG).show() }

    private fun render() {
        val shell = stack().apply { setBackgroundColor(canvas) }
        setContentView(shell)
        val head = stack().apply { setPadding(dp(20),dp(19),dp(20),dp(15)); setBackgroundColor(canvas) }
        head.addView(label("SALO", 27f, pine, true))
        head.addView(block("Climate planning, made simple", 13f, muted))
        shell.addView(head)
        val scroll = ScrollView(this).apply { fillViewport=true; isVerticalScrollBarEnabled=false }
        content = stack().apply { setPadding(dp(18),dp(2),dp(18),dp(16)) }
        scroll.addView(content)
        shell.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        bottomBar = LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL; setBackgroundColor(white); setPadding(dp(10),dp(7),dp(10),dp(7)); elevation=dp(6).toFloat()
        }
        listOf("⌂  Home", "✓  My plan", "▤  Report").forEachIndexed { ix, name ->
            val item = label(name,14f,if(ix==tab) pine else muted,ix==tab).apply {
                gravity=Gravity.CENTER; minimumHeight=dp(52); isClickable=true; isFocusable=true; setOnClickListener { tab=ix; closeKeyboard(); render() }
                contentDescription="Open $name"
            }
            bottomBar.addView(item,LinearLayout.LayoutParams(0,dp(52),1f))
        }
        shell.addView(bottomBar)
        when(tab) { 0 -> home(); 1 -> plans(); else -> report() }
    }

    private fun home() {
        content.addView(block("What is happening in your community?", 22f, navy, true))
        content.addView(block("Choose a scenario. SALO prepares an action plan from your inputs.",14f,muted))
        content.addView(space(6))
        content.addView(card {
            addView(block("1. Choose climate scenario",13f,muted,true))
            addView(row(
                button("☀  El Niño", input.mode==ClimateMode.DRY){ changeMode(ClimateMode.DRY) },
                button("☂  La Niña", input.mode==ClimateMode.WET){ changeMode(ClimateMode.WET) }
            ))
            addView(space(12))
            addView(block("2. Select example municipality",13f,muted,true))
            val spinner=Spinner(this@MainActivity).apply {
                adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,locs)
                setSelection(locs.indexOf(input.location))
                onItemSelectedListener=object: android.widget.AdapterView.OnItemSelectedListener {
                    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                    override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, pos: Int, id: Long) {
                        if (locs[pos]!=input.location) changeLocation(locs[pos])
                    }
                }
                background=background(canvas,12,edge)
                minimumHeight=dp(52)
            }
            addView(spinner,LinearLayout.LayoutParams(-1,dp(52)))
            addView(space())
            addView(block("3. Two quick details",13f,muted,true))
            if (input.mode==ClimateMode.DRY) dryInputs(this) else wetInputs(this)
        })
        content.addView(button("Get my action plan  →") { createPlan(readQuick = true) })
        content.addView(space(13))
        val status = ClimateEngine.evaluate(input)
        content.addView(card {
            addView(block("SALO recommends",12f,pine,true))
            addView(block(status.headline,20f,navy,true))
            addView(block(status.metric,17f,pine,true))
            addView(block(status.subline,13f,muted))
            addView(space(6))
            addView(button("Why this recommendation?",false) { AlertDialog.Builder(this@MainActivity).setTitle("How SALO calculated this").setMessage(status.explanation).setPositiveButton("Got it",null).show() })
        })
        content.addView(card {
            addView(block("Speak or type instead",18f,navy,true))
            addView(block("Example: 'El Niño sa Bulalacao. May 8000 liters. Daily demand 2500 liters. Gumawa ng plano.'",13f,muted))
            val entry = textInput("", "Tell SALO what you know…")
            spokenInput = entry
            addView(entry);addView(space(10))
            addView(row(
                button("🎙 Offline voice",false){ startSpeech() },
                button("Review instruction"){ reviewCommand(entry.text.toString()) }
            ))
            addView(space(7))
            addView(block("Only supported phrases are recognized. Speech requires an Android on-device recognizer; typed instructions always work offline.",12f,muted))
        })
        content.addView(block("Demo inputs, not live PAGASA forecasts or emergency alerts.",12f,muted))
    }

    private fun dryInputs(group:LinearLayout) {
        val stored=field(group,"Usable stored water (liters)",clean(input.storedLiters),"e.g. 8000")
        val daily=field(group,"Daily water demand (liters)",clean(input.dailyLiters),"e.g. 2500")
        quickFirst=stored;quickSecond=daily;quickDrainage=null
    }
    private fun wetInputs(group:LinearLayout) {
        val rain=field(group,"Example rainfall (mm)",clean(input.rainMm),"e.g. 85")
        group.addView(block("Drainage observation",13f,muted,true))
        val options=listOf("Needs inspection", "Checked clear", "Reported blocked")
        val choices=Spinner(this).apply {
            adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,options)
            setSelection(when(input.drainage) { Drainage.REVIEW->0;Drainage.CLEAR->1;Drainage.BLOCKED->2 })
            background=background(canvas,12,edge)
        }
        group.addView(choices,LinearLayout.LayoutParams(-1,dp(52))); group.addView(space(12))
        quickFirst=rain;quickSecond=null;quickDrainage=choices
    }
    private fun clean(n:Double):String = if(n%1.0==0.0) n.toLong().toString() else n.toString()

    private fun plans() {
        content.addView(block("My action plan",23f,navy,true))
        content.addView(block("${input.location}  ·  ${if (input.mode==ClimateMode.DRY) "El Niño exercise" else "La Niña exercise"}",14f,muted))
        content.addView(space())
        if (!hasCurrentPlan()) {
            content.addView(card {addView(block("No current plan saved",18f,navy,true));addView(block("Go to Home, verify your inputs, and tap Get my action plan.",14f,muted));addView(space(12));addView(button("Back to Home"){tab=0;render()})})
            return
        }
        val result=ClimateEngine.evaluate(input)
        val done=finishedTasks()
        content.addView(card {
            addView(block(result.headline,19f,navy,true))
            addView(block(result.metric,21f,pine,true))
            addView(block("${done.size.coerceAtMost(result.tasks.size)} of ${result.tasks.size} tasks self-reported complete",13f,muted))
        })
        result.tasks.sortedBy { it.priority }.forEachIndexed { index, task ->
            content.addView(card {
                val check=CheckBox(this@MainActivity).apply {
                    text = "${index + 1}. ${task.title}"; textSize=15f; setTextColor(navy); setTypeface(null,Typeface.BOLD)
                    isChecked=done.contains(task.id)
                    setOnCheckedChangeListener { _, checked ->
                        val n=finishedTasks();if(checked) n.add(task.id) else n.remove(task.id);persistDone(n);render()
                    }
                }
                addView(check)
                addView(block("Owner: ${task.owner}",12f,pine,true))
                addView(block(task.reason,13f,muted))
            })
        }
        content.addView(button("View my report  →"){tab=2;render()})
        content.addView(space(10))
        content.addView(block("Completing a checkbox records a self-report, not field verification or a confirmed safety outcome.",12f,muted))
    }

    private fun report() {
        content.addView(block("Preparedness report",23f,navy,true))
        content.addView(block("A simple record you can share with your team.",14f,muted))
        content.addView(space())
        val result=ClimateEngine.evaluate(input)
        val taskCount=if(hasCurrentPlan()) result.tasks.size else 0
        val done=if(hasCurrentPlan()) finishedTasks().size.coerceAtMost(taskCount) else 0
        content.addView(card {
            addView(block(input.location,21f,navy,true))
            addView(block(if(input.mode==ClimateMode.DRY) "El Niño / water planning" else "La Niña / rainfall planning",14f,muted))
            addView(space(8));addView(block(result.metric,24f,pine,true));addView(block(result.headline,16f,navy,true))
            addView(block("Actions completed: $done / $taskCount",14f,navy,true))
            addView(space(8));addView(block(result.explanation,13f,muted))
        })
        content.addView(button("Share report") {
            val report=reportText(result,done,taskCount)
            val intent=Intent(Intent.ACTION_SEND).apply { type="text/plain";putExtra(Intent.EXTRA_SUBJECT,"SALO ${input.location} planning report");putExtra(Intent.EXTRA_TEXT,report) }
            startActivity(Intent.createChooser(intent,"Share SALO report"))
        })
        content.addView(space(12))
        content.addView(card {
            addView(block("What this means",16f,navy,true))
            addView(block("Data is stored only on this phone. Official climate advisories, engineering verification, and water-quality assessments are not included. This is a planning demonstration.",13f,muted))
        })
        content.addView(button("Start another assessment",false){tab=0;render()})
    }
    private fun reportText(r:ClimateResult,done:Int,total:Int):String = buildString {
        appendLine("SALO | Climate-to-Action")
        appendLine("Location: ${input.location} (fictional planning inputs)")
        appendLine("Scenario: ${input.mode}")
        appendLine("Recommendation: ${r.headline}")
        appendLine("Estimate: ${r.metric}")
        appendLine("Tasks completed (self-reported): $done/$total")
        appendLine("Reasoning: ${r.explanation}")
        if(hasCurrentPlan())r.tasks.forEach { appendLine("- [${if(finishedTasks().contains(it.id)) "x" else " "}] ${it.title} — ${it.owner}") }
        appendLine("NOT AN OFFICIAL CLIMATE WARNING OR FORECAST. Confirm with PAGASA and local DRRM.")
    }

    private fun reviewCommand(raw:String) {
        if(raw.isBlank()){msg("Speak or type an instruction first.");return}
        val draft=CommandParser.parse(raw)
        if (draft.warnings.isNotEmpty()) {
            AlertDialog.Builder(this).setTitle("Please clarify").setMessage(draft.warnings.joinToString("\n")).setPositiveButton("OK",null).show();return
        }
        if(!draft.hasChanges()) {msg("I could not understand that instruction. Try the example shown.");return}
        val location=draft.location ?: input.location
        val base=if(location!=input.location)readLocation(location).copy(mode=input.mode) else input
        val proposed=base.copy(
            mode=draft.mode ?: input.mode, storedLiters=draft.storedLiters ?: base.storedLiters,
            dailyLiters=draft.dailyLiters ?: base.dailyLiters, rainMm=draft.rainMm ?: base.rainMm,
            drainage=draft.drainage ?: base.drainage,
            tankCapacityLiters=maxOf(base.tankCapacityLiters,draft.storedLiters ?: base.storedLiters)
        )
        if(!proposed.valid()) {msg("The recognized values are invalid; please check the amounts.");return}
        val changes=mutableListOf<String>()
        draft.location?.let {changes += "Location: $it"}
        draft.mode?.let {changes += "Scenario: ${if(it==ClimateMode.DRY) "El Niño" else "La Niña"}"}
        draft.storedLiters?.let{changes += "Stored water: ${clean(it)} liters"}
        draft.dailyLiters?.let{changes += "Daily use: ${clean(it)} liters/day"}
        draft.rainMm?.let{changes += "Scenario rain: ${clean(it)} mm"}
        draft.drainage?.let{changes += "Drainage: $it"}
        if(changes.isEmpty())changes+="Generate a new action plan using the current values."
        val proposedResult=ClimateEngine.evaluate(proposed)
        AlertDialog.Builder(this)
            .setTitle("Confirm what SALO understood")
            .setMessage(changes.joinToString("\n")+"\n\nCalculated: ${proposedResult.metric}\n${proposedResult.headline}\n\nApply these details and generate a plan?")
            .setNegativeButton("Edit",null)
            .setPositiveButton("Approve") {_,_->input=proposed;save();createPlan()}
            .show()
    }

    private fun startSpeech() {
        if(Build.VERSION.SDK_INT<31 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            AlertDialog.Builder(this).setTitle("Offline voice unavailable")
                .setMessage("This Android device has no available on-device speech recognizer. SALO will not use an online recognition service. You can still type instructions offline.")
                .setPositiveButton("Use keyboard",null).show()
            return
        }
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) {
            pendingMicrophone=true; requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),71);return
        }
        beginOnDeviceSpeech()
    }
    override fun onRequestPermissionsResult(requestCode:Int, permissions:Array<out String>, grantResults:IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions,grantResults)
        if(requestCode==71){ val pending=pendingMicrophone;pendingMicrophone=false
            if(pending && grantResults.firstOrNull()==PackageManager.PERMISSION_GRANTED) startSpeech() else msg("Microphone permission denied. You can type instead.") }
    }
    private fun beginOnDeviceSpeech() {
        try {
            speech?.destroy()
            speech = SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
            speech?.setRecognitionListener(object:RecognitionListener {
                override fun onReadyForSpeech(params:Bundle?) {msg("Listening… Speak a short instruction.")}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB:Float) {}
                override fun onBufferReceived(buffer:ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults:Bundle?) {}
                override fun onEvent(eventType:Int,params:Bundle?) {}
                override fun onError(error:Int) {runOnUiThread { msg("On-device speech could not recognize that. You can type instead. (Code $error)") }}
                override fun onResults(results:Bundle?) {
                    val transcript=results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    runOnUiThread {
                        if(transcript.isNotBlank()) {
                            val entry=spokenInput
                            entry?.setText(transcript);entry?.setSelection(transcript.length)
                            AlertDialog.Builder(this@MainActivity).setTitle("Recognized instruction")
                                .setMessage("$transcript\n\nPlease check transcription before continuing.")
                                .setNegativeButton("Edit",null)
                                .setPositiveButton("Review"){_,_->reviewCommand(transcript)}.show()
                        }else msg("No words recognized. Try typing.")
                    }
                }
            })
            val intent=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-PH")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,false)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true)
            }
            speech?.startListening(intent)
        } catch (e:Exception) {msg("Offline recognition not supported here. Use keyboard instead.")}
    }
    override fun onDestroy() {speech?.destroy();speech=null;super.onDestroy()}
}
