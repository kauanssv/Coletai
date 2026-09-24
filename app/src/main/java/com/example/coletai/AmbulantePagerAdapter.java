package com.example.coletai;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class AmbulantePagerAdapter extends FragmentStateAdapter {

    public AmbulantePagerAdapter(
            @NonNull FragmentActivity fragmentActivity
    ) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {

        switch (position) {

            case 0:
                return new InicioAmbulanteFragment();

            case 1:
                return new HistoricoAmbulanteFragment();

            case 2:
                return new PerfilAmbulanteFragment();

            default:
                return new InicioAmbulanteFragment();
        }
    }

    @Override
    public int getItemCount() {
        return 3;
    }
}