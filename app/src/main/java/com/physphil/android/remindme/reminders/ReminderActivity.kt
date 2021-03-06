package com.physphil.android.remindme.reminders

import android.app.DatePickerDialog
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.DatePicker
import android.widget.TimePicker
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.physphil.android.remindme.BaseActivity
import com.physphil.android.remindme.R
import com.physphil.android.remindme.RemindMeApplication
import com.physphil.android.remindme.models.PresetTime
import com.physphil.android.remindme.models.Recurrence
import com.physphil.android.remindme.reminders.list.DeleteReminderDialogFragment
import com.physphil.android.remindme.util.getDisplayDate
import com.physphil.android.remindme.util.getDisplayTime
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_reminder.*

/**
 * Create a new [Reminder] or edit an existing one.
 *
 * Copyright (c) 2017 Phil Shadlyn
 */
class ReminderActivity : BaseActivity(), TimePickerDialog.OnTimeSetListener,
        DatePickerDialog.OnDateSetListener, RecurrencePickerDialog.OnRecurrenceSetListener,
        DeleteReminderDialogFragment.Listener {

    /** A [CompositeDisposable] to contain all active subscriptions. Should be cleared when Activity is destroyed */
    private val disposables = CompositeDisposable()

    private val viewModel: ReminderViewModel by lazy {
        val id = intent.getStringExtra(EXTRA_REMINDER_ID)
        val presetTime = PresetTime.fromId(
            intent.getIntExtra(EXTRA_REMINDER_PRESET_TIME, PresetTime.ID_UNKNOWN)
        )
        ViewModelProviders.of(
            this,
            ReminderViewModelFactory(application as RemindMeApplication, id, presetTime)
        )
            .get(ReminderViewModel::class.java)
    }
    private val notificationManager: NotificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reminder)
        setHomeArrowBackNavigation()
        bindViews()
        bindViewModel()
    }

    override fun onDestroy() {
        super.onDestroy()
        disposables.clear()
    }

    private fun bindViews() {
        reminderTitleView.setOnTextChangedListener {
            viewModel.updateTitle(it)
        }

        reminderBodyView.setOnTextChangedListener {
            viewModel.updateBody(it)
        }

        reminderTimeView.setOnClickListener {
            viewModel.openTimePicker()
        }

        reminderDateView.setOnClickListener {
            viewModel.openDatePicker()
        }

        reminderRecurrenceView.setOnClickListener {
            viewModel.openRecurrencePicker()
        }
    }

    private fun bindViewModel() {
        // Will either be called immediately with stored value, or will be updated upon successful read from database
        disposables.add(viewModel.observableReminder
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    // on success. Save Reminder in viewmodel and update UI
                    viewModel.reminder = it
                    reminderTitleView.setText(it.title, true)
                    reminderBodyView.setText(it.body)
                    reminderTimeView.setText(it.getDisplayTime(this))
                    reminderDateView.setText(it.getDisplayDate(this))
                    reminderRecurrenceView.setText(it.recurrence.displayString)

                    // Clear any notifications for this Reminder
                    notificationManager.cancel(it.notificationId)
                }, {
                    // on error
                }))

        viewModel.getReminderTime().observe(this, Observer { it?.let { reminderTimeView.setText(it) } })
        viewModel.getReminderDate().observe(this, Observer { it?.let { reminderDateView.setText(it) } })
        viewModel.getReminderRecurrence().observe(this, Observer { it?.let { reminderRecurrenceView.setText(it) } })
        viewModel.getToolbarTitle().observe(this, Observer { it?.let { setToolbarTitle(it) } })

        viewModel.clearNotificationEvent.observe(this, Observer {
            it?.let {
                notificationManager.cancel(it)
            }
        })

        viewModel.confirmDeleteEvent.observe(this, Observer {
            DeleteReminderDialogFragment.newInstance().show(supportFragmentManager, DeleteReminderDialogFragment.TAG)
        })

        viewModel.closeActivityEvent.observe(this, Observer {
            finish()
        })

        viewModel.openTimePickerEvent.observe(this, Observer {
            it?.let {
                TimePickerDialog(this,
                        R.style.Pickers,
                        this,
                        it.hour,
                        it.minute,
                        false).show()
            }
        })

        viewModel.openDatePickerEvent.observe(this, Observer {
            it?.let {
                DatePickerDialog(this,
                        R.style.Pickers,
                        this,
                        it.year,
                        it.month,
                        it.day).show()

            }
        })

        viewModel.openRecurrencePickerEvent.observe(this, Observer {
            it?.let {
                RecurrencePickerDialog.newInstance(it)
                        .show(supportFragmentManager, RecurrencePickerDialog.TAG)
            }
        })
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.menu_reminder, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        super.onPrepareOptionsMenu(menu)
        val delete = menu.findItem(R.id.menu_delete)
        delete?.let { viewModel.prepareOptionsMenuItems(delete) }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_save -> {
                viewModel.saveReminder()
                finish()
                true
            }
            R.id.menu_delete -> {
                viewModel.confirmDeleteReminder()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // region OnTimeSetListener implementation
    override fun onTimeSet(view: TimePicker?, hourOfDay: Int, minute: Int) {
        viewModel.updateTime(this, hourOfDay, minute)
    }
    // endregion

    // region OnDateSetListener implementation
    override fun onDateSet(view: DatePicker?, year: Int, month: Int, dayOfMonth: Int) {
        viewModel.updateDate(this, year, month, dayOfMonth)
    }
    // endregion

    // region OnRecurrenceSetListener implementation
    override fun onRecurrenceSet(recurrence: Recurrence) {
        viewModel.updateRecurrence(recurrence)
    }
    // endregion

    // region DeleteReminderDialogFragment.Listener implementation
    override fun onDeleteReminder() {
        viewModel.deleteReminder()
    }

    override fun onCancel() {
        // do nothing
    }
    // endregion

    companion object {
        private const val EXTRA_REMINDER_ID = "com.physphil.android.remindme.EXTRA_REMINDER_ID"
        private const val EXTRA_REMINDER_PRESET_TIME = "com.physphil.android.remindme.EXTRA_REMINDER_PRESET_TIME"

        fun intent(context: Context, reminderId: String? = null): Intent {
            val intent = Intent(context, ReminderActivity::class.java)
            intent.putExtra(EXTRA_REMINDER_ID, reminderId)
            return intent
        }
    }
}