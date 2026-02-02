# Play Store Submission Checklist

## Before You Start
- [ ] Google Play Developer account ($25 one-time fee)
- [ ] Privacy policy hosted online

## Assets Needed

### App Icon (Already Done)
- [x] 512x512 PNG icon for Play Store listing

### Screenshots (Required)
- [ ] Minimum 2 screenshots
- [ ] Phone: 16:9 aspect ratio recommended
- [ ] Tablet: 16:9 or 4:3 (optional but recommended)

### Feature Graphic
- [ ] 1024x500 PNG (displayed at top of store listing)
- [ ] Can use your icon centered with a background

## Create Feature Graphic (Quick Method)

You can create a simple feature graphic using ImageMagick:
```bash
# Create a 1024x500 dark blue background with your icon centered
convert -size 1024x500 xc:'#1a3a4a' \
  /home/abdza/Pictures/tadabbur_icon.png -gravity center -geometry 300x300+0+0 -composite \
  store_assets/feature_graphic.png
```

## Play Console Steps

1. **Go to** https://play.google.com/console

2. **Create App**
   - App name: Tadabbur
   - Default language: English
   - App type: App
   - Free

3. **Set Up Your App** (Dashboard tasks)
   - [ ] Privacy policy URL
   - [ ] App access (All functionality available without restrictions)
   - [ ] Ads (No ads)
   - [ ] Content rating questionnaire
   - [ ] Target audience: Not designed for children
   - [ ] News app: No
   - [ ] COVID-19 app: No
   - [ ] Data safety form

4. **Store Listing**
   - [ ] App name: Tadabbur
   - [ ] Short description (from PLAY_STORE_LISTING.md)
   - [ ] Full description (from PLAY_STORE_LISTING.md)
   - [ ] App icon (512x512)
   - [ ] Feature graphic (1024x500)
   - [ ] Screenshots (minimum 2)
   - [ ] App category: Books & Reference
   - [ ] Contact email

5. **Release**
   - [ ] Create production release
   - [ ] Upload app-tadabbur-release.apk (or .aab bundle)
   - [ ] Add release notes (e.g., "Initial release")
   - [ ] Review and roll out

## App Bundle (Alternative to APK)

Google prefers App Bundles (.aab) over APKs:
```bash
./gradlew bundleTadabburRelease -PdisableFirebase
```
Output: `app/build/outputs/bundle/tadabburRelease/app-tadabbur-release.aab`

## After Submission

- Review typically takes a few hours to a few days
- You'll receive an email when approved or if issues found

## Files Created

| File | Purpose |
|------|---------|
| PRIVACY_POLICY.md | Host this online |
| PLAY_STORE_LISTING.md | Copy text for store listing |
| store_assets/ | Screenshots and graphics |
