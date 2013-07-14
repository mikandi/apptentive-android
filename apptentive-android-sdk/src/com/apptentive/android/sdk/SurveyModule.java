/*
 * Copyright (c) 2013, Apptentive, Inc. All Rights Reserved.
 * Please refer to the LICENSE file for the terms and conditions
 * under which redistribution and use of this file is permitted.
 */

package com.apptentive.android.sdk;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.view.View;
import android.widget.*;

import com.apptentive.android.sdk.model.Event;
import com.apptentive.android.sdk.module.metric.MetricModule;
import com.apptentive.android.sdk.module.survey.*;
import com.apptentive.android.sdk.module.survey.view.MultichoiceSurveyQuestionView2;
import com.apptentive.android.sdk.module.survey.view.MultiselectSurveyQuestionView2;
import com.apptentive.android.sdk.module.survey.view.SurveyDialog;
import com.apptentive.android.sdk.module.survey.view.TextSurveyQuestionView2;
import com.apptentive.android.sdk.offline.SurveyPayload;
import com.apptentive.android.sdk.storage.PayloadStore;
import com.apptentive.android.sdk.util.Util;

import java.util.HashMap;
import java.util.Map;


/**
 * This module is responsible for displaying Surveys.
 *
 * @author Sky Kelsey
 */
public class SurveyModule {

	// *************************************************************************************************
	// ********************************************* Static ********************************************
	// *************************************************************************************************

	private static SurveyModule instance;

	public static SurveyModule getInstance() {
		if (instance == null) {
			instance = new SurveyModule();
		}
		return instance;
	}


	// *************************************************************************************************
	// ********************************************* Private *******************************************
	// *************************************************************************************************

	private SurveyDefinition surveyDefinition;
	private SurveyState surveyState;
	private Map<String, String> data;
	private OnSurveyFinishedListener onSurveyFinishedListener;

	private SurveyModule() {
	}

	private void cleanup() {
		this.surveyDefinition = null;
		this.surveyState = null;
		this.onSurveyFinishedListener = null;
		this.data = null;
	}

	// *************************************************************************************************
	// ******************************************* Not Private *****************************************
	// *************************************************************************************************

	public void show(Context context, SurveyDefinition surveyDefinition, OnSurveyFinishedListener onSurveyFinishedListener) {
		this.surveyDefinition = surveyDefinition;
		this.surveyState = new SurveyState(surveyDefinition);
		this.onSurveyFinishedListener = onSurveyFinishedListener;
		data = new HashMap<String, String>();
		data.put("id", surveyDefinition.getId());

		Intent intent = new Intent();
		intent.setClass(context, ViewActivity.class);
		intent.putExtra("module", ViewActivity.Module.SURVEY.toString());
		context.startActivity(intent);
	}

	public SurveyState getSurveyState() {
		return this.surveyState;
	}

	public boolean isCompleted() {
		for (Question question : surveyDefinition.getQuestions()) {
			String questionId = question.getId();
			boolean required = question.isRequired();
			boolean answered = surveyState.isAnswered(questionId);
			if (required && !answered) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Shows a survey embedded in a dialog.
	 * @param context The Activity context from which this method is called.
	 */
	public void showSurveyAsDialog(final Context context, final SurveyDefinition surveyDefinition, OnSurveyFinishedListener onSurveyFinishedListener) {
		this.surveyDefinition = surveyDefinition;
		this.surveyState = new SurveyState(surveyDefinition);
		this.onSurveyFinishedListener = onSurveyFinishedListener;
		data = new HashMap<String, String>();
		data.put("id", surveyDefinition.getId());

		final SurveyDialog dialog = new SurveyDialog(context, surveyDefinition);
		SurveyDialog.OnActionPerformedListener listener = new SurveyDialog.OnActionPerformedListener() {
			@Override
			public void onAboutApptentiveButtonPressed() {
				// TODO
				Log.e("About button pressed.");
			}

			@Override
			public void onSurveySubmitted() {
				Log.e("Survey Submitted.");
				Util.hideSoftKeyboard((Activity) context, dialog.getCurrentFocus());
				MetricModule.sendMetric(context, Event.EventLabel.survey__submit, null, data);

				getSurveyStore(context).addPayload(new SurveyPayload(surveyDefinition));

				if(SurveyModule.this.onSurveyFinishedListener != null) {
					SurveyModule.this.onSurveyFinishedListener.onSurveyFinished(true);
				}
				if (surveyDefinition.isShowSuccessMessage() && surveyDefinition.getSuccessMessage() != null) {
					AlertDialog.Builder builder = new AlertDialog.Builder(context);
					builder.setMessage(surveyDefinition.getSuccessMessage());
					builder.setTitle(context.getString(R.string.apptentive_survey_success_title));
					builder.setPositiveButton(context.getString(R.string.apptentive_survey_positive_button), new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialogInterface, int i) {
							cleanup();
						}
					});
					builder.show();
				} else {
					cleanup();
				}

				dialog.dismiss();
			}

			@Override
			public void onSurveySkipped() {
				dialog.dismiss();
				if(SurveyModule.this.onSurveyFinishedListener != null) {
					SurveyModule.this.onSurveyFinishedListener.onSurveyFinished(false);
				}
			}

			@Override
			public void onQuestionAnswered(Question question) {
				sendMetricForQuestion(context, question);
			}
		};
		dialog.setOnActionPerformedListener(listener);

		MetricModule.sendMetric(context, Event.EventLabel.survey__launch, null, data);
		SurveyHistory.recordSurveyDisplay(context, surveyDefinition.getId(), System.currentTimeMillis());
		dialog.show();
	}

	void doShow(final Activity activity) {
		activity.setContentView(R.layout.apptentive_survey_dialog);

		if (surveyDefinition == null) {
			return;
		}

		TextView title = (TextView) activity.findViewById(R.id.title);
		title.setFocusable(true);
		title.setFocusableInTouchMode(true);
		title.setText(surveyDefinition.getName());

		String descriptionText = surveyDefinition.getDescription();
		TextView description = (TextView) activity.findViewById(R.id.description);
		if (descriptionText != null) {
			description.setText(descriptionText);
		} else {
			description.setVisibility(View.GONE);
		}

		final Button send = (Button) activity.findViewById(R.id.send);
		send.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				Log.d("Survey Submitted.");
				Util.hideSoftKeyboard(activity, view);
				MetricModule.sendMetric(activity, Event.EventLabel.survey__submit, null, data);

				getSurveyStore(activity).addPayload(new SurveyPayload(surveyDefinition));

				if(SurveyModule.this.onSurveyFinishedListener != null) {
					SurveyModule.this.onSurveyFinishedListener.onSurveyFinished(true);
				}

				if (surveyDefinition.isShowSuccessMessage() && surveyDefinition.getSuccessMessage() != null) {
					AlertDialog.Builder builder = new AlertDialog.Builder(activity);
					builder.setMessage(surveyDefinition.getSuccessMessage());
					builder.setTitle(view.getContext().getString(R.string.apptentive_survey_success_title));
					builder.setPositiveButton(view.getContext().getString(R.string.apptentive_survey_positive_button), new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialogInterface, int i) {
							cleanup();
							activity.finish();
						}
					});
					builder.show();
				} else {
					cleanup();
					activity.finish();
				}
			}
		});

		View about = activity.findViewById(R.id.apptentive_branding_view);
		about.setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				AboutModule.getInstance().show(activity);
			}
		});

		LinearLayout questions = (LinearLayout) activity.findViewById(R.id.questions);

		// Then render all the questions
		for (final Question question : surveyDefinition.getQuestions()) {
			if (question.getType() == Question.QUESTION_TYPE_SINGLELINE) {
				TextSurveyQuestionView2 textQuestionView = new TextSurveyQuestionView2(activity, (SinglelineQuestion) question);
				textQuestionView.setOnSurveyQuestionAnsweredListener(new OnSurveyQuestionAnsweredListener() {
					public void onAnswered() {
						sendMetricForQuestion(activity, question);
						send.setEnabled(isCompleted());
					}
				});
				questions.addView(textQuestionView);
			} else if (question.getType() == Question.QUESTION_TYPE_MULTICHOICE) {
				MultichoiceSurveyQuestionView2 multichoiceQuestionView = new MultichoiceSurveyQuestionView2(activity, (MultichoiceQuestion) question);
				multichoiceQuestionView.setOnSurveyQuestionAnsweredListener(new OnSurveyQuestionAnsweredListener() {
					public void onAnswered() {
						sendMetricForQuestion(activity, question);
						send.setEnabled(isCompleted());
					}
				});
				questions.addView(multichoiceQuestionView);
			} else if (question.getType() == Question.QUESTION_TYPE_MULTISELECT) {
				MultiselectSurveyQuestionView2 multiselectQuestionView = new MultiselectSurveyQuestionView2(activity, (MultiselectQuestion) question);
				multiselectQuestionView.setOnSurveyQuestionAnsweredListener(new OnSurveyQuestionAnsweredListener() {
					public void onAnswered() {
						sendMetricForQuestion(activity, question);
						send.setEnabled(isCompleted());
					}
				});
				questions.addView(multiselectQuestionView);
			} else if (question.getType() == Question.QUESTION_TYPE_STACKRANK) {
				// TODO: This.
			}
		}
		MetricModule.sendMetric(activity, Event.EventLabel.survey__launch, null, data);
		SurveyHistory.recordSurveyDisplay(activity, surveyDefinition.getId(), System.currentTimeMillis());

		// Force the top of the survey to be shown first.
		title.requestFocus();
	}

	void sendMetricForQuestion(Context context, Question question) {
		String questionId = question.getId();
		if(!surveyState.isMetricSent(questionId) && surveyState.isAnswered(questionId)) {
			Map<String, String> answerData = new HashMap<String, String>();
			answerData.put("id", question.getId());
			answerData.put("survey_id", surveyDefinition.getId());
			MetricModule.sendMetric(context, Event.EventLabel.survey__question_response, null, answerData);
			surveyState.markMetricSent(questionId);
		}
	}

	private static PayloadStore getSurveyStore(Context context) {
		return Apptentive.getDatabase(context);
	}

	void onBackPressed(Context context) {
		MetricModule.sendMetric(context, Event.EventLabel.survey__cancel, null, data);
		if(SurveyModule.this.onSurveyFinishedListener != null) {
			SurveyModule.this.onSurveyFinishedListener.onSurveyFinished(false);
		}
		cleanup();
	}
}
