# Twee-weg Synchronisatie & Persistente Login

Dit plan beschrijft de verbeteringen om de login-status te behouden en de kijkstatus volledig te synchroniseren tussen de app en YouTube.

## Voorgestelde Wijzigingen

### Auth & Persistence
#### [MODIFY] [AuthManager.kt](file:///home/sander/AndroidStudioProjects/MyYoutube/app/src/main/java/nl/baasmail/myyoutube/ui/auth/AuthManager.kt)
- Toevoegen van een `autoSignIn` functie die bij het opstarten probeert een actieve sessie te herstellen via `CredentialManager`.
- Het Access Token direct vernieuwen als er een sessie is.

### Synchronisatie Logica
#### [MODIFY] [YouTubeRepository.kt](file:///home/sander/AndroidStudioProjects/MyYoutube/app/src/main/java/nl/baasmail/myyoutube/data/repository/YouTubeRepository.kt)
- **Late Sync:** Bij inloggen alle lokale `isWatched` video's zonder `playlistItemId` naar YouTube sturen.
- **Pull Sync:** Tijdens `refreshAll` de inhoud van de "MyYouTube Watched" playlist ophalen.
- De lokale database bijwerken op basis van de YouTube-lijst (twee-weg).

#### [MODIFY] [HomeViewModel.kt](file:///home/sander/AndroidStudioProjects/MyYoutube/app/src/main/java/nl/baasmail/myyoutube/ui/home/HomeViewModel.kt)
- Aanroepen van `autoSignIn` bij initialisatie.
- Triggeren van de volledige synchronisatie na een succesvolle login.

## Verificatieplan
### Handmatige Verificatie
1. **Persistentie:** Inloggen, app afsluiten, app opnieuw starten. Foto moet direct zichtbaar zijn zonder opnieuw inloggen.
2. **Late Sync:** Uitloggen, video vinken, inloggen. Video moet op YouTube verschijnen.
3. **Remote Sync:** Op YouTube website video uit de playlist halen. In de app verversen. Vinkje moet verdwijnen.
4. **Remote Sync 2:** Op YouTube website video toevoegen aan de playlist. In de app verversen. Vinkje moet verschijnen.
