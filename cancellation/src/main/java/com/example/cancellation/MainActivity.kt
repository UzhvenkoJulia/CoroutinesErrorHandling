package com.example.cancellation

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.cancellation.databinding.ActivityMainBinding
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val TAG = "CoroutinesTask"

    private var calculationJob: Job? = null

    // ловить краші, які не були спіймані звичайним try-catch
    private val ceh = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "глобальне перехоплення крашу: ${throwable.message}")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnStart.setOnClickListener {
            calculationJob = lifecycleScope.launch(Dispatchers.Default) {
                try {
                    for (i in 1..100) {
                        // без корутин
                        Thread.sleep(50)

                        // чи не натис Cancel - викине CancellationException і перерве цикл
                        ensureActive()

                        Log.d(TAG, "обробка: $i%")
                    }
                    Log.d(TAG, "роботу успішно завершено")

                } catch (e: CancellationException) {
                    Log.d(TAG, "обробку скасовано користувачем")
                    throw e
                }
            }
        }

        binding.btnCancel.setOnClickListener {
            calculationJob?.cancel()
        }


        binding.btnTestCrash.setOnClickListener {
            lifecycleScope.launch {
                // якщо одна корутина тут впаде, вона не потягне за собою сусідів
                supervisorScope {

                    launch {
                        try {
                            delay(1000)
                            throw RuntimeException("банер не завантажився")
                        } catch (e: Exception) {
                            // обов'язково ловлю помилку, бо supervisorScope не ковтає краші сам
                            Log.e(TAG, "спіймано помилку банера: ${e.message}")
                        }
                    }

                    launch {
                        delay(2000)
                        // supervisorScope врятував цю корутину від крашу першої
                        Log.d(TAG, "стрічка новин завантажена")
                    }
                }
            }
        }


        binding.btnGlobalCrash.setOnClickListener {
            val safeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main + ceh)

            safeScope.launch {
                delay(500)
                throw IllegalArgumentException("упс")
            }
        }


        binding.btnBonusTrap.setOnClickListener {
            lifecycleScope.launch { // коренева (Top-level) корутина

                launch(ceh) {  // пастка
                    delay(100)
                    throw RuntimeException("вкладений краш")
                }
            }
        }
    }
}

/*
У коді вище є кнопка btnBonusTrap. Якщо її натиснути, додаток вилетить (crash), а лог з ceh не з'явиться
Це відбувається через 2-ге Золоте правило CoroutineExceptionHandler. Цей обробник працює тільки якщо його передати у найвищу корутину
Передала ceh у вкладену дочірню корутину launch(ceh). Коли в ній сталася помилка, вона проігнорувала цей локальний ceh і автоматично передала помилку "вгору" своєму батькові (lifecycleScope.launch).
У батька не було свого обробника, помилка вилетіла в систему і "вбила" додаток
*/