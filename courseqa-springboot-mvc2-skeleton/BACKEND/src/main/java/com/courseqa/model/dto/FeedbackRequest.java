package com.courseqa.model.dto;

import com.courseqa.model.entity.FeedbackReason;

public class FeedbackRequest {

    private Boolean helpful;
    private FeedbackReason reasonCode;
    private String comment;

    public Boolean getHelpful() { return helpful; }
    public void setHelpful(Boolean helpful) { this.helpful = helpful; }

    public FeedbackReason getReasonCode() { return reasonCode; }
    public void setReasonCode(FeedbackReason reasonCode) { this.reasonCode = reasonCode; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}