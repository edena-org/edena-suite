package org.edena.ada.server.models;

import java.util.*;

public class UserPOJO {
    private String _id;
    private String userId;
    private String oidcUserName;
    private String name;
    private String email;
    private List<String> roles = new ArrayList<>();
    private List<String> permissions = new ArrayList<>();
    private Boolean locked = false;
    private String passwordHash;

    public UserPOJO() {}

    public UserPOJO(String userId, String name, String email) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.locked = false;
    }

    // Getters
    public String get_id() { return _id; }
    public String getUserId() { return userId; }
    public String getOidcUserName() { return oidcUserName; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public List<String> getRoles() { return roles; }
    public List<String> getPermissions() { return permissions; }
    public Boolean getLocked() { return locked; }
    public String getPasswordHash() { return passwordHash; }

    // Setters
    public void set_id(String _id) { this._id = _id; }
    public void setUserId(String userId) { this.userId = userId; }
    public void setOidcUserName(String oidcUserName) { this.oidcUserName = oidcUserName; }
    public void setName(String name) { this.name = name; }
    public void setEmail(String email) { this.email = email; }
    public void setRoles(List<String> roles) { this.roles = roles; }
    public void setPermissions(List<String> permissions) { this.permissions = permissions; }
    public void setLocked(Boolean locked) { this.locked = locked; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    @Override
    public String toString() {
        return userId != null ? userId : "User";
    }
}