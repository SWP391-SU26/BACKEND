/*
    Bootstrap administrator account for local development.

    The password is stored as a BCrypt hash; plaintext credentials are never
    committed to the repository. Re-running this migration keeps this account
    active and grants it a single active ADMIN role.
*/
SET XACT_ABORT ON;

BEGIN TRY
    BEGIN TRANSACTION;

    DECLARE @Email NVARCHAR(255) = N'hainambl996@gmail.com';
    DECLARE @PasswordHash NVARCHAR(100) = N'$2a$10$qYFBBROqKoDqhGwG8ImQ7.FP1G13nzs/CrXRpj4cmTGgIkiHXJsG2';
    DECLARE @UserId UNIQUEIDENTIFIER;

    SELECT @UserId = user_id
    FROM users
    WHERE email = @Email;

    IF @UserId IS NULL
    BEGIN
        INSERT INTO users (full_name, email, password_hash, is_active, created_at, updated_at)
        VALUES (N'Hai Nam', @Email, @PasswordHash, 1, GETDATE(), GETDATE());

        SELECT @UserId = user_id
        FROM users
        WHERE email = @Email;
    END
    ELSE
    BEGIN
        UPDATE users
        SET full_name = N'Hai Nam',
            password_hash = @PasswordHash,
            is_active = 1,
            updated_at = GETDATE()
        WHERE user_id = @UserId;
    END;

    UPDATE user_roles
    SET is_active = 0
    WHERE user_id = @UserId
      AND is_active = 1;

    INSERT INTO user_roles (user_id, role_name, permission_json, assigned_at, is_active)
    VALUES (@UserId, N'ADMIN', N'{"all":true}', GETDATE(), 1);

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
GO
