package sbsv5;

import lombok.Data;

@Data
public class UserMessage {
    int id;
    private String userName;
    private Byte userAge;


    public UserMessage setId(int id) {
        this.id = id;
        return this;
    }

    public UserMessage setUserName(String userName) {
        this.userName = userName;
        return this;
    }

    public UserMessage setUserAge(Byte userAge) {
        this.userAge = userAge;
        return this;
    }
}
