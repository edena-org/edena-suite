package org.edena.ada.server.models;

import java.sql.Timestamp;
import java.util.Date;

public class HtmlSnippetPOJO {
    private String _id;
    private String snippetId;
    private String content;
    private Boolean active = true;
    private String createdById;
    private Date timeCreated;

    public HtmlSnippetPOJO() {
        this.timeCreated = new Date();
    }

    public HtmlSnippetPOJO(String snippetId, String content) {
        this.snippetId = snippetId;
        this.content = content;
        this.active = true;
        this.timeCreated = new Date();
    }

    // Getters
    public String get_id() { return _id; }
    public String getSnippetId() { return snippetId; }
    public String getContent() { return content; }
    public Boolean getActive() { return active; }
    public String getCreatedById() { return createdById; }
    public Date getTimeCreated() { return timeCreated; }

    // Setters
    public void set_id(String _id) { this._id = _id; }
    public void setSnippetId(String snippetId) { this.snippetId = snippetId; }
    public void setContent(String content) { this.content = content; }
    public void setActive(Boolean active) { this.active = active; }
    public void setCreatedById(String createdById) { this.createdById = createdById; }
    public void setTimeCreated(Date timeCreated) { this.timeCreated = timeCreated; }
    public void setTimeCreated(Timestamp timeCreated) { this.timeCreated = new Date(timeCreated.getTime()); }

    @Override
    public String toString() {
        return snippetId != null ? snippetId : "HtmlSnippet";
    }
}