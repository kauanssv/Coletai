package com.example.coletai;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class ClientePagerAdapter extends FragmentStateAdapter {

    public ClientePagerAdapter(
            @NonNull FragmentActivity fragmentActivity
    ) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {

        switch (position) {

            case 0:
                return new InicioClienteFragment();

            case 1:
                return new HistoricoClienteFragment();

            case 2:
                return new PerfilClienteFragment();

            default:
                return new InicioClienteFragment();
        }
    }

    @Override
    public int getItemCount() {
        return 3;
    }
}