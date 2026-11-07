package com.example.coletai;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class EmpresaPagerAdapter extends FragmentStateAdapter {

    public EmpresaPagerAdapter(
            @NonNull FragmentActivity fragmentActivity
    ) {

        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(
            int position
    ) {

        switch (position) {

            case 0:

                return new InicioEmpresaFragment();

            case 1:

                return new HistoricoEmpresaFragment();

            case 2:

                return new PerfilEmpresaFragment();

            default:

                return new InicioEmpresaFragment();
        }
    }

    @Override
    public int getItemCount() {

        return 3;
    }
}