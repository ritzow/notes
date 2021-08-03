CREATE PROCEDURE update_user_note (IN name VARCHAR(50), IN note CLOB, OUT id INTEGER)
	MODIFIES SQL DATA
BEGIN ATOMIC
	DECLARE id_temp INTEGER DEFAULT NULL;
	SELECT userid INTO id_temp FROM userdata WHERE username = name;
	IF id_temp IS NULL THEN
		INSERT INTO userdata (username) VALUES (name);
		SET id_temp = IDENTITY();
		INSERT INTO NOTES (userid, notedata) VALUES (id_temp, note);
	ELSE
		UPDATE NOTES SET (userid, notedata) = (id_temp, note);
	END IF;
	SET id = id_temp;
END