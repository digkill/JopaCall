// 1. --- ГЛАВНОЕ ИСПРАВЛЕНИЕ: Добавлена декларация пакета ---
// Эта строка ОБЯЗАТЕЛЬНА и должна соответствовать структуре папок вашего проекта.
package org.mediarise.dialer.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.mediarise.dialer.R

// 2. Убран ненужный импорт. CallActivity находится в том же пакете.
// import org.mediarise.dialer.ui.CallActivity

class StartActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Устанавливаем макет, который мы создали ранее
        setContentView(R.layout.activity_start)

        // Находим элементы интерфейса по их ID
        val roomEditText = findViewById<EditText>(R.id.room_id_edit_text)
        val joinButton = findViewById<Button>(R.id.join_button)

        // Устанавливаем слушатель нажатия на кнопку
        joinButton.setOnClickListener {
            val roomId = roomEditText.text.toString().trim()

            // Проверяем, что пользователь ввел ID комнаты
            if (roomId.isNotEmpty()) {
                // Создаем намерение (Intent) для запуска CallActivity
                val intent = Intent(this, CallActivity::class.java).apply {
                    // Кладем ID комнаты в Intent, чтобы CallActivity могла его получить
                    putExtra("room", roomId)
                }
                startActivity(intent)
            } else {
                // Если поле пустое, показываем пользователю подсказку
                Toast.makeText(this, "Введите ID комнаты", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
