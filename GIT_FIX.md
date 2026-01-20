# Fix Git Permission Issue for SecurityScansWithTemporal

## Current Issue
```
Permission to mehab/SecurityScansWithTemporal.git denied to ssbande
```

The repository is configured to use HTTPS: `https://github.com/mehab/SecurityScansWithTemporal.git`

## Quick Fix Options

### Option 1: Switch to SSH (Recommended if you have SSH keys)

1. **Check if you have SSH keys:**
   ```bash
   ls -la ~/.ssh/id_rsa.pub
   ```

2. **If you have SSH keys, switch remote to SSH:**
   ```bash
   cd /Users/shreyasbande/meha/workspace/projects/temporal/security-scan-project
   git remote set-url origin git@github.com:mehab/SecurityScansWithTemporal.git
   ```

3. **Test SSH connection:**
   ```bash
   ssh -T git@github.com
   ```

4. **Try your git operation again:**
   ```bash
   git push
   ```

### Option 2: Use Personal Access Token (For HTTPS)

GitHub no longer accepts passwords for HTTPS. You need a Personal Access Token:

1. **Create a Personal Access Token:**
   - Go to GitHub → Settings → Developer settings → Personal access tokens → Tokens (classic)
   - Click "Generate new token (classic)"
   - Select scopes: `repo` (full control of private repositories)
   - Generate and **copy the token** (you won't see it again!)

2. **Update credentials:**
   ```bash
   cd /Users/shreyasbande/meha/workspace/projects/temporal/security-scan-project
   
   # Remove old credentials from keychain
   git credential-osxkeychain erase
   host=github.com
   protocol=https
   # Press Enter twice
   
   # Next time you push, use:
   # Username: ssbande (or your GitHub username)
   # Password: <paste your personal access token>
   ```

3. **Or configure credential helper to store token:**
   ```bash
   git config --global credential.helper osxkeychain
   # Then push - you'll be prompted once, credentials will be saved
   ```

### Option 3: Check Repository Access

1. **Verify you have access:**
   - Go to https://github.com/mehab/SecurityScansWithTemporal
   - Check if you can see the repository
   - If it's private, ensure your account has access

2. **Check if you're using the correct GitHub account:**
   ```bash
   git config --global user.name
   git config --global user.email
   ```

## Recommended: Switch to SSH

If you have SSH keys set up with GitHub, this is the easiest solution:

```bash
cd /Users/shreyasbande/meha/workspace/projects/temporal/security-scan-project
git remote set-url origin git@github.com:mehab/SecurityScansWithTemporal.git
git push
```

## If You Don't Have SSH Keys

1. **Generate SSH key:**
   ```bash
   ssh-keygen -t rsa -b 4096 -C "meha.bhargava2@gmail.com"
   # Press Enter to accept default location
   # Optionally set a passphrase
   ```

2. **Add SSH key to GitHub:**
   ```bash
   cat ~/.ssh/id_rsa.pub
   # Copy the output
   ```
   - Go to GitHub → Settings → SSH and GPG keys
   - Click "New SSH key"
   - Paste your public key
   - Save

3. **Switch remote to SSH:**
   ```bash
   cd /Users/shreyasbande/meha/workspace/projects/temporal/security-scan-project
   git remote set-url origin git@github.com:mehab/SecurityScansWithTemporal.git
   ```

4. **Test and push:**
   ```bash
   ssh -T git@github.com
   git push
   ```
