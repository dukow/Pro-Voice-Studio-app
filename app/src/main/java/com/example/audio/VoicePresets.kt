package com.example.audio

data class VoicePreset(
    val id: String,
    val name: String,
    val category: String,
    val description: String,
    val filterString: String
)

object VoicePresets {
    const val CAT_GENDER_AGE = "Gender & Age"
    const val CAT_SCIFI_SPACE = "Sci-Fi & Space"
    const val CAT_ENVIRONMENT = "Environments"
    const val CAT_DEVICES = "Devices & Amps"
    const val CAT_SPEED_PITCH = "Speed & Pitch"
    const val CAT_COMICAL = "Fun & Comical"

    val presets = listOf(
        // === GENDER & AGE ===
        VoicePreset(
            id = "child",
            name = "Child",
            category = CAT_GENDER_AGE,
            description = "High-pitched, energetic voice",
            filterString = "asetrate=44100*1.4,atempo=0.71,aresample=44100"
        ),
        VoicePreset(
            id = "woman",
            name = "Woman",
            category = CAT_GENDER_AGE,
            description = "Feminine, higher-pitched tone",
            filterString = "asetrate=44100*1.25,atempo=0.8,aresample=44100"
        ),
        VoicePreset(
            id = "girl",
            name = "Young Girl",
            category = CAT_GENDER_AGE,
            description = "Soft, cheerful high-pitched voice",
            filterString = "asetrate=44100*1.35,atempo=0.74,aresample=44100"
        ),
        VoicePreset(
            id = "deep_man",
            name = "Deep Man",
            category = CAT_GENDER_AGE,
            description = "Low-pitched, masculine resonance",
            filterString = "asetrate=44100*0.82,atempo=1.22,aresample=44100"
        ),
        VoicePreset(
            id = "old_man",
            name = "Old Man",
            category = CAT_GENDER_AGE,
            description = "Slightly shaky, weathered voice",
            filterString = "asetrate=44100*0.85,atempo=1.17,tremolo=f=6:d=0.3,aresample=44100"
        ),
        VoicePreset(
            id = "old_woman",
            name = "Old Woman",
            category = CAT_GENDER_AGE,
            description = "Thin, raspy high-pitch voice",
            filterString = "asetrate=44100*1.2,atempo=0.83,tremolo=f=7:d=0.25,aresample=44100"
        ),
        VoicePreset(
            id = "monster",
            name = "Monster",
            category = CAT_GENDER_AGE,
            description = "Heavy, roaring low pitch",
            filterString = "asetrate=44100*0.65,atempo=1.54,aresample=44100,volume=1.3"
        ),
        VoicePreset(
            id = "giant",
            name = "Giant",
            category = CAT_GENDER_AGE,
            description = "Slow, booming voice with earth shaking rumble",
            filterString = "asetrate=44100*0.58,atempo=1.72,aecho=0.8:0.9:800:0.3,aresample=44100"
        ),

        // === SCI-FI & SPACE ===
        VoicePreset(
            id = "robot",
            name = "Retro Robot",
            category = CAT_SCIFI_SPACE,
            description = "Metallic, modulated robotic synthesis",
            filterString = "asetrate=44100*1.1,atempo=0.9,apulsator=hz=45:amount=0.8,aresample=44100"
        ),
        VoicePreset(
            id = "alien",
            name = "Alien Overlord",
            category = CAT_SCIFI_SPACE,
            description = "Eerie, vibrating extraterrestrial tone",
            filterString = "vibrato=f=12:d=0.6,asetrate=44100*1.2,atempo=0.83,aresample=44100"
        ),
        VoicePreset(
            id = "cyborg",
            name = "Cyborg",
            category = CAT_SCIFI_SPACE,
            description = "Double-pitched synthetic voice",
            filterString = "aecho=0.8:0.88:20:0.4,asetrate=44100*0.9,atempo=1.11,aresample=44100"
        ),
        VoicePreset(
            id = "astronaut",
            name = "Astronaut",
            category = CAT_SCIFI_SPACE,
            description = "Radio transmission with cosmic space echo",
            filterString = "highpass=f=400,lowpass=f=2500,aecho=0.7:0.7:600:0.3"
        ),
        VoicePreset(
            id = "dalek",
            name = "Evil Mutant",
            category = CAT_SCIFI_SPACE,
            description = "Harsh, vibrating ring modulation effect",
            filterString = "vibrato=f=35:d=0.9,highpass=f=200,lowpass=f=3500"
        ),
        VoicePreset(
            id = "space_ship",
            name = "Starship AI",
            category = CAT_SCIFI_SPACE,
            description = "Soft electronic helper with light resonance",
            filterString = "asetrate=44100*1.15,atempo=0.87,aecho=0.8:0.7:150:0.2,aresample=44100"
        ),
        VoicePreset(
            id = "cosmic_void",
            name = "Cosmic Void",
            category = CAT_SCIFI_SPACE,
            description = "Swallowing deep reverberated space",
            filterString = "aecho=0.8:0.9:3000:0.4,lowpass=f=4000"
        ),
        VoicePreset(
            id = "laser_resonance",
            name = "Laser Resonant",
            category = CAT_SCIFI_SPACE,
            description = "Pulsing sci-fi frequency resonance",
            filterString = "apulsator=hz=15:amount=0.9,equalizer=f=3000:width_type=h:width=1000:g=6"
        ),

        // === ENVIRONMENTS ===
        VoicePreset(
            id = "cave",
            name = "Cave",
            category = CAT_ENVIRONMENT,
            description = "Distant cave-like deep echo",
            filterString = "aecho=0.8:0.9:1000:0.3"
        ),
        VoicePreset(
            id = "cathedral",
            name = "Cathedral",
            category = CAT_ENVIRONMENT,
            description = "Grand church multi-layered echo and reverb",
            filterString = "aecho=0.8:0.9:1400:0.35,aecho=0.8:0.8:1800:0.25"
        ),
        VoicePreset(
            id = "bathroom",
            name = "Bathroom",
            category = CAT_ENVIRONMENT,
            description = "Highly reflective tile-wall bounce echo",
            filterString = "aecho=0.7:0.7:60:0.5"
        ),
        VoicePreset(
            id = "underwater",
            name = "Underwater",
            category = CAT_ENVIRONMENT,
            description = "Muffled and bubbling aqueous filter",
            filterString = "lowpass=f=450,vibrato=f=6:d=0.45,volume=1.2"
        ),
        VoicePreset(
            id = "tunnel",
            name = "Echo Tunnel",
            category = CAT_ENVIRONMENT,
            description = "Long concrete tunnel acoustic bounce",
            filterString = "aecho=0.8:0.8:250:0.35,highpass=f=120"
        ),
        VoicePreset(
            id = "empty_room",
            name = "Empty Room",
            category = CAT_ENVIRONMENT,
            description = "Subtle room reflection and flutter echo",
            filterString = "aecho=0.7:0.4:120:0.2"
        ),
        VoicePreset(
            id = "forest",
            name = "Deep Forest",
            category = CAT_ENVIRONMENT,
            description = "Broad open air scattering echo",
            filterString = "aecho=0.6:0.5:2000:0.2"
        ),
        VoicePreset(
            id = "grand_canyon",
            name = "Grand Canyon",
            category = CAT_ENVIRONMENT,
            description = "Extremely long bouncing canyon echo",
            filterString = "aecho=0.8:0.9:2500:0.45"
        ),

        // === DEVICES & AMPS ===
        VoicePreset(
            id = "telephone",
            name = "Telephone",
            category = CAT_DEVICES,
            description = "Old 80s dial-up phone sound",
            filterString = "highpass=f=400,lowpass=f=3400"
        ),
        VoicePreset(
            id = "radio",
            name = "AM Radio",
            category = CAT_DEVICES,
            description = "Slightly crackly frequency band filter",
            filterString = "highpass=f=650,lowpass=f=2200,volume=2.2"
        ),
        VoicePreset(
            id = "megaphone",
            name = "Megaphone",
            category = CAT_DEVICES,
            description = "Screaming police loudspeaker box",
            filterString = "highpass=f=500,lowpass=f=3500,volume=3.5"
        ),
        VoicePreset(
            id = "walkie_talkie",
            name = "Walkie Talkie",
            category = CAT_DEVICES,
            description = "Narrow mid-range responder radio",
            filterString = "highpass=f=900,lowpass=f=2000,volume=2.5"
        ),
        VoicePreset(
            id = "vintage_gramophone",
            name = "Gramophone",
            category = CAT_DEVICES,
            description = "1920s phonograph thin horn",
            filterString = "highpass=f=1100,lowpass=f=2800,volume=1.5"
        ),
        VoicePreset(
            id = "smart_speaker",
            name = "Smart Assistant",
            category = CAT_DEVICES,
            description = "Crisp, dynamic high-range speaker projection",
            filterString = "equalizer=f=1200:width_type=h:width=300:g=5,compand=attacks=0:decays=0:points=-30/-10|0/0"
        ),
        VoicePreset(
            id = "distorted_guitar",
            name = "Guitar Amp Overdrive",
            category = CAT_DEVICES,
            description = "Crunchy overdrive distortion effect",
            filterString = "volume=4.0,alimiter=level_in=1:level_out=0.8:limit=0.85"
        ),
        VoicePreset(
            id = "stadium",
            name = "Stadium PA",
            category = CAT_DEVICES,
            description = "Loudspeaker announce across dynamic field",
            filterString = "aecho=0.8:0.8:600:0.3,aecho=0.7:0.7:1200:0.2,volume=1.8"
        ),

        // === SPEED & PITCH ===
        VoicePreset(
            id = "slow_mo",
            name = "Slow Motion",
            category = CAT_SPEED_PITCH,
            description = "0.7x slower speech speed, pitch fixed",
            filterString = "atempo=0.7"
        ),
        VoicePreset(
            id = "fast_motion",
            name = "Speedy Runner",
            category = CAT_SPEED_PITCH,
            description = "1.4x faster speech speed, pitch fixed",
            filterString = "atempo=1.4"
        ),
        VoicePreset(
            id = "drunk",
            name = "Drunk slur",
            category = CAT_SPEED_PITCH,
            description = "Wobbly, slow pitch and tempo modulation",
            filterString = "atempo=0.85,vibrato=f=4:d=0.5"
        ),
        VoicePreset(
            id = "nervous",
            name = "Nervous Rush",
            category = CAT_SPEED_PITCH,
            description = "Stressed-out fast tempo and high jitter",
            filterString = "atempo=1.35,asetrate=44100*1.1,atempo=0.91,aresample=44100"
        ),

        // === FUN & COMICAL ===
        VoicePreset(
            id = "chipmunk",
            name = "Chipmunk",
            category = CAT_COMICAL,
            description = "Squeaky cartoon chipmunk voice",
            filterString = "asetrate=44100*1.68,atempo=0.595,aresample=44100"
        ),
        VoicePreset(
            id = "helium",
            name = "Helium Balloon",
            category = CAT_COMICAL,
            description = "Super squeaky, cartoon balloon pitch",
            filterString = "asetrate=44100*1.85,atempo=0.54,aresample=44100"
        ),
        VoicePreset(
            id = "goblin",
            name = "Goblin",
            category = CAT_COMICAL,
            description = "High mischievous critter voice",
            filterString = "asetrate=44100*1.48,atempo=0.67,vibrato=f=9:d=0.3,aresample=44100"
        ),
        VoicePreset(
            id = "zombie",
            name = "Zombie",
            category = CAT_COMICAL,
            description = "Undead, growling voice filter",
            filterString = "asetrate=44100*0.72,atempo=1.38,tremolo=f=4:d=0.5,aresample=44100"
        ),
        VoicePreset(
            id = "ghost",
            name = "Ghost",
            category = CAT_COMICAL,
            description = "Spectral, floating scary echo wave",
            filterString = "asetrate=44100*0.88,atempo=1.13,aecho=0.8:0.9:1300:0.45,vibrato=f=5:d=0.3,aresample=44100"
        ),
        VoicePreset(
            id = "vampire",
            name = "Vampire Lord",
            category = CAT_COMICAL,
            description = "Dark, velvet voice with echo depth",
            filterString = "asetrate=44100*0.78,atempo=1.28,equalizer=f=180:width_type=h:width=120:g=8,aecho=0.8:0.6:350:0.2,aresample=44100"
        ),
        VoicePreset(
            id = "whisper_boost",
            name = "Whisper Boost",
            category = CAT_COMICAL,
            description = "Swells soft murmurs, limits peak clicks",
            filterString = "compand=attacks=0:decays=0:points=-45/-8|-25/-3|0/0,volume=1.8"
        ),
        VoicePreset(
            id = "pro_studio",
            name = "Pro Studio Vocal",
            category = CAT_COMICAL,
            description = "Warm low-end, sparkling clear highs",
            filterString = "compand=attacks=0.1:decays=0.3:points=-35/-12|0/0,equalizer=f=80:width_type=h:width=100:g=-4,equalizer=f=3500:width_type=h:width=1500:g=4"
        )
    )
}
